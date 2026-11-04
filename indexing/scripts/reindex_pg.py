"""Rewrite search store rows from the portal documents. Safe to rerun: each document is upserted with its chunks replaced.

Targets are portal `document` rows that are not deleted and not folders. Originals are read from MinIO and
chunked by the same code as the indexing queue. The portal database is the one SEARCH_PG_* points to.

    cd indexing && python -m scripts.reindex_pg --dry-run
    python -m scripts.reindex_pg --batch-size 200
    python -m scripts.reindex_pg --from-id <last id printed by an interrupted run>
    python -m scripts.reindex_pg --ids-from-marks --target es
    python -m scripts.reindex_pg --include-index-only
"""
import argparse
import json
import sys

import psycopg
from dataclasses import dataclass, field
from typing import Callable, Dict, Iterator, List, Optional, Tuple

from app.config import settings
from app.models.schemas import FullDocument

ACTIVE_DOCUMENT = "coalesce(d.is_deleted, false) = false and coalesce(d.is_folder, false) = false"


@dataclass
class Report:
    target: str
    targets: int = 0
    succeeded: int = 0
    removed: int = 0
    index_only_copied: int = 0
    failures: List[Tuple[str, str]] = field(default_factory=list)
    last_id: Optional[str] = None


def target_rows(conn, from_id: Optional[str], batch_size: int) -> Iterator[List[tuple]]:
    last = from_id or ""
    while True:
        rows = conn.execute(f"select d.id, d.bucket, d.file_path from document d where {ACTIVE_DOCUMENT} and d.id > %s "
                            f"order by d.id limit %s", (last, batch_size)).fetchall()
        if not rows:
            return
        yield rows
        last = rows[-1][0]


def marked_rows(conn, target: str) -> List[tuple]:
    """Marked documents with their portal row; bucket is None when the document is deleted, a folder or gone."""
    return conn.execute(f"select m.document_id, d.bucket, d.file_path from search_reindex_state m "
                        f"left join document d on d.id = m.document_id and {ACTIVE_DOCUMENT} "
                        f"where m.target = %s order by m.document_id", (target,)).fetchall()


def reindex(conn, store, marks, build: Callable[[str, str], tuple], *, batch_size: int, from_id: Optional[str],
            from_marks: bool, report: Report) -> Report:
    batches = [marked_rows(conn, store.name)] if from_marks else target_rows(conn, from_id, batch_size)
    for rows in batches:
        for document_id, bucket, file_path in rows:
            report.targets += 1
            try:
                if bucket is None:
                    store.delete_document(document_id)
                    report.removed += 1
                else:
                    document, chunks = build(bucket, file_path)
                    store.index_document(document, chunks)
                    report.succeeded += 1
                marks.set_reindex_mark(document_id, store.name, None)
            except Exception as e:
                report.failures.append((document_id, str(e)))
            report.last_id = document_id
        print(f"progress: {report.targets} done, last id {report.last_id}", file=sys.stderr)
    return report


def copy_index_only(conn, es_documents: Iterator[Dict], store, report: Report) -> None:
    """Copy ES full documents that have no portal row. They get no chunks, as in ES."""
    for source in es_documents:
        exists = conn.execute("select 1 from document where id = %s", (source["document_id"],)).fetchone()
        if exists:
            continue
        store.index_document(FullDocument(document_id=source["document_id"], title=source.get("title") or "",
                                          content=source.get("content") or "", metadata=source.get("metadata") or {}), [])
        report.index_only_copied += 1


def es_full_documents() -> Iterator[Dict]:
    from elasticsearch.helpers import scan
    from app.services.full_document_client import full_document_client
    for hit in scan(full_document_client.client, index=full_document_client.index_name, query={"query": {"match_all": {}}}):
        yield hit["_source"]


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--batch-size", type=int, default=100)
    parser.add_argument("--from-id")
    parser.add_argument("--ids-from-marks", action="store_true")
    parser.add_argument("--target", choices=("postgres", "es"), default="postgres")
    parser.add_argument("--include-index-only", action="store_true")
    args = parser.parse_args(argv)

    from app.search.pg_store import PgSearchBackend, set_search_path
    pg = PgSearchBackend.from_settings(settings)
    pg.ensure_schema()
    with psycopg.connect(pg.pool.conninfo, autocommit=True) as conn:
        set_search_path(conn, settings.search_pg_schema)
        if args.dry_run:
            count = conn.execute(f"select count(*) from document d where {ACTIVE_DOCUMENT}").fetchone()[0]
            first = [r[0] for r in conn.execute(f"select d.id from document d where {ACTIVE_DOCUMENT} order by d.id limit 10")]
            marks = conn.execute("select target, count(*) from search_reindex_state group by 1").fetchall()
            print(json.dumps({"dry_run": True, "targets": count, "first_ids": first, "reindex_marks": dict(marks)}, ensure_ascii=False))
            return 0

        if args.target == "es":
            from app.search.es_store import EsSearchBackend
            store = EsSearchBackend()
        else:
            store = pg
        from app.services.document_processor import build_document
        report = reindex(conn, store, pg, lambda bucket, path: build_document(bucket, path, {})[:2],
                         batch_size=args.batch_size, from_id=args.from_id, from_marks=args.ids_from_marks,
                         report=Report(target=args.target))
        if args.include_index_only and args.target == "postgres":
            copy_index_only(conn, es_full_documents(), pg, report)
        indexed = pg.stats()
    print(json.dumps({**report.__dict__, "search_document": indexed.get("document_count"),
                      "search_chunk": indexed.get("chunk_count")}, ensure_ascii=False))
    return 1 if report.failures else 0


if __name__ == "__main__":
    sys.exit(main())
