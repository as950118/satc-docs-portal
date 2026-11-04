"""제목이 든 예전 객체 키(documents/{id}/{제목}.{ext})를 ASCII 키(documents/{id}/{id}.{ext})로 옮긴다.

STORAGE_ASCII_KEYS=true 로 바꾼 뒤, 그 전에 올라온 문서에 한 번 돌린다. 문서마다 다음 순서로 진행한다.

1. 객체를 새 키로 복사한다. 예전 객체는 되돌릴 수 있게 지우지 않는다.
2. Indexing Server 에 재색인을 요청한다 (문서 ID 로 예전 청크 삭제 → 새 키를 title 과 함께 처리).
3. document.file_path 를 새 키로 바꾼다. 여기가 완료 지점이라, 중간에 멈추면 다음 실행이 남은 단계를 다시 한다.

기본은 계획만 출력한다. --apply 를 줘야 바꾼다. 업로드와 코드 동기화를 멈춘 상태에서 돌린다.

    cd indexing
    pip install -r scripts/requirements.txt
    DATABASE_URL=postgresql://postgres:postgres@localhost:5432/docs \\
    STORAGE_ENDPOINT=http://localhost:9000 STORAGE_ACCESS_KEY=... STORAGE_SECRET_KEY=... \\
    python -m scripts.migrate_ascii_keys [--indexing-url http://localhost:8000] [--apply]

저장소 설정은 Indexing Server 와 같은 STORAGE_*(비어 있으면 MINIO_*) 환경변수를 쓴다.
"""
import argparse
import os
import sys
from dataclasses import dataclass
from typing import Iterable, List, Optional, Protocol, TextIO

import requests


@dataclass(frozen=True)
class DocumentRow:
    id: str
    title: str
    extension: str
    file_path: str


@dataclass(frozen=True)
class Move:
    document: DocumentRow
    target_key: str

    @property
    def source_key(self) -> str:
        return self.document.file_path


@dataclass(frozen=True)
class Outcome:
    move: Move
    status: str  # moved | source-missing | conflict | failed
    detail: str = ""


class Storage(Protocol):
    def exists(self, bucket: str, key: str) -> bool: ...

    def copy(self, bucket: str, source_key: str, target_key: str) -> None: ...


class Documents(Protocol):
    def files(self) -> List[DocumentRow]: ...

    def move_file_path(self, document_id: str, source_key: str, target_key: str) -> bool: ...


class Indexer(Protocol):
    def reindex(self, bucket: str, document: DocumentRow, key: str) -> None: ...


def ascii_key(document: DocumentRow) -> str:
    """백엔드 StorageSettings.documentKey(asciiKeys=true) 와 같은 규칙"""
    return f"documents/{document.id}/{document.id}.{document.extension}"


def plan(documents: Iterable[DocumentRow]) -> List[Move]:
    return [Move(d, ascii_key(d)) for d in documents if d.file_path != ascii_key(d)]


def storage_state(move: Move, storage: Storage, bucket: str) -> str:
    if storage.exists(bucket, move.target_key):
        return "copied"
    return "copy" if storage.exists(bucket, move.source_key) else "source-missing"


def apply_move(move: Move, storage: Storage, bucket: str, documents: Documents, indexer: Indexer) -> Outcome:
    state = storage_state(move, storage, bucket)
    if state == "source-missing":
        return Outcome(move, "source-missing")
    if state == "copy":
        storage.copy(bucket, move.source_key, move.target_key)
    indexer.reindex(bucket, move.document, move.target_key)
    if not documents.move_file_path(move.document.id, move.source_key, move.target_key):
        return Outcome(move, "conflict", "file_path 가 실행 중에 바뀌었습니다")
    return Outcome(move, "moved")


def run(documents: Documents, storage: Storage, bucket: str, indexer: Indexer, apply: bool, out: TextIO) -> int:
    moves = plan(documents.files())
    print(f"옮길 문서 {len(moves)}건 (bucket={bucket})", file=out)
    if not apply:
        for move in moves:
            state = storage_state(move, storage, bucket)
            print(f"[{state}] {move.document.id} {move.source_key} -> {move.target_key}", file=out)
        print("dry-run 입니다. 바꾸려면 --apply 를 주세요.", file=out)
        return 0

    outcomes = []
    for move in moves:
        try:
            outcome = apply_move(move, storage, bucket, documents, indexer)
        except Exception as e:
            outcome = Outcome(move, "failed", f"{type(e).__name__}: {e}")
        outcomes.append(outcome)
        print(f"[{outcome.status}] {move.document.id} {move.source_key} -> {move.target_key} {outcome.detail}".rstrip(), file=out)
    moved = sum(o.status == "moved" for o in outcomes)
    print(f"완료 {moved}건, 남은 문서 {len(outcomes) - moved}건. 남은 문서는 원인을 고친 뒤 다시 실행하세요.", file=out)
    return 0 if moved == len(outcomes) else 1


class PostgresDocuments:
    def __init__(self, url: str):
        import psycopg

        self.connection = psycopg.connect(url, autocommit=True)

    def files(self) -> List[DocumentRow]:
        rows = self.connection.execute(
            "select id, title, coalesce(extension, ''), file_path from document "
            "where coalesce(is_folder, false) = false and coalesce(is_deleted, false) = false and file_path is not null "
            "order by created_at, id"
        ).fetchall()
        return [DocumentRow(*row) for row in rows]

    def move_file_path(self, document_id: str, source_key: str, target_key: str) -> bool:
        # version 을 올려 이 행을 먼저 읽어 둔 백엔드 저장이 file_path 를 되돌리지 못하게 한다 (@Version)
        cursor = self.connection.execute(
            "update document set file_path = %s, version = version + 1 where id = %s and file_path = %s",
            (target_key, document_id, source_key),
        )
        return cursor.rowcount == 1


class HttpIndexer:
    def __init__(self, base_url: str, timeout: float = 30):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    def reindex(self, bucket: str, document: DocumentRow, key: str) -> None:
        deleted = requests.delete(f"{self.base_url}/api/v1/documents",
                                  json={"files": [f"{document.id}.{document.extension}"]}, timeout=self.timeout)
        deleted.raise_for_status()
        queued = requests.post(f"{self.base_url}/api/v1/process-document",
                               json={"bucketName": bucket, "objectName": key, "title": document.title}, timeout=self.timeout)
        queued.raise_for_status()


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="제목 기반 객체 키를 ASCII 키로 옮긴다. 기본은 dry-run.")
    parser.add_argument("--database-url", default=os.environ.get("DATABASE_URL"),
                        help="PostgreSQL 주소 (기본: DATABASE_URL 환경변수)")
    parser.add_argument("--indexing-url", default=os.environ.get("INDEXING_URL", "http://localhost:8000"),
                        help="Indexing Server 주소 (기본: INDEXING_URL 또는 http://localhost:8000)")
    parser.add_argument("--apply", action="store_true", help="실제로 복사·재색인·DB 갱신을 한다")
    args = parser.parse_args(argv)
    if not args.database_url:
        parser.error("--database-url 또는 DATABASE_URL 이 필요합니다.")

    from app.config import storage_config
    from app.services.object_storage import object_storage

    return run(PostgresDocuments(args.database_url), object_storage, storage_config.bucket,
               HttpIndexer(args.indexing_url), args.apply, sys.stdout)


if __name__ == "__main__":
    sys.exit(main())
