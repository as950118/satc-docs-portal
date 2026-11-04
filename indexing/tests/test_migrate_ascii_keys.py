"""scripts/migrate_ascii_keys 의 계획·적용·재실행을 가짜 저장소·DB·Indexing Server 로 확인한다.

실행: python -m pytest tests/test_migrate_ascii_keys.py
"""
import io

from scripts.migrate_ascii_keys import DocumentRow, run

BUCKET = "docs"
A = "0f1e2d3c-4b5a-6978-8a9b-0c1d2e3f4a5b"
B = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"


class FakeStorage:
    def __init__(self, objects):
        self.objects = dict(objects)
        self.copies = 0

    def exists(self, bucket, key):
        return (bucket, key) in self.objects

    def copy(self, bucket, source_key, target_key):
        self.copies += 1
        self.objects[(bucket, target_key)] = self.objects[(bucket, source_key)]


class FakeDocuments:
    def __init__(self, rows):
        self.rows = {r.id: r for r in rows}

    def files(self):
        return list(self.rows.values())

    def move_file_path(self, document_id, source_key, target_key):
        row = self.rows[document_id]
        if row.file_path != source_key:
            return False
        self.rows[document_id] = DocumentRow(row.id, row.title, row.extension, target_key)
        return True


class FakeIndexer:
    def __init__(self, fail=False):
        self.calls = []
        self.fail = fail

    def reindex(self, bucket, document, key):
        if self.fail:
            raise RuntimeError("indexing down")
        self.calls.append((bucket, document.id, key, document.title))


def _setup():
    title_doc = DocumentRow(A, "배포 가이드(v2)", "md", f"documents/{A}/배포 가이드(v2).md")
    ascii_doc = DocumentRow(B, "이미 옮긴 문서", "yaml", f"documents/{B}/{B}.yaml")
    storage = FakeStorage({(BUCKET, title_doc.file_path): b"title", (BUCKET, ascii_doc.file_path): b"ascii"})
    return FakeDocuments([title_doc, ascii_doc]), storage


def _run(documents, storage, indexer, apply):
    out = io.StringIO()
    code = run(documents, storage, BUCKET, indexer, apply, out)
    return code, out.getvalue()


def test_dry_run_only_prints_the_plan_for_title_keys():
    documents, storage = _setup()
    indexer = FakeIndexer()

    code, out = _run(documents, storage, indexer, apply=False)

    assert code == 0
    assert f"[copy] {A} documents/{A}/배포 가이드(v2).md -> documents/{A}/{A}.md" in out
    assert B not in out
    assert storage.copies == 0 and indexer.calls == []
    assert documents.rows[A].file_path == f"documents/{A}/배포 가이드(v2).md"


def test_apply_copies_reindexes_with_title_and_updates_the_path_then_reruns_as_no_op():
    documents, storage = _setup()
    indexer = FakeIndexer()

    code, _ = _run(documents, storage, indexer, apply=True)

    assert code == 0
    assert storage.objects[(BUCKET, f"documents/{A}/{A}.md")] == b"title"
    assert (BUCKET, f"documents/{A}/배포 가이드(v2).md") in storage.objects
    assert documents.rows[A].file_path == f"documents/{A}/{A}.md"
    assert indexer.calls == [(BUCKET, A, f"documents/{A}/{A}.md", "배포 가이드(v2)")]

    code, out = _run(documents, storage, indexer, apply=True)

    assert code == 0
    assert "옮길 문서 0건" in out
    assert storage.copies == 1 and len(indexer.calls) == 1


def test_a_run_stopped_after_the_copy_finishes_without_copying_again():
    documents, storage = _setup()
    storage.objects[(BUCKET, f"documents/{A}/{A}.md")] = b"title"

    code, _ = _run(documents, storage, FakeIndexer(), apply=True)

    assert code == 0
    assert storage.copies == 0
    assert documents.rows[A].file_path == f"documents/{A}/{A}.md"


def test_reindex_failure_keeps_the_old_path_so_the_next_run_retries():
    documents, storage = _setup()

    code, out = _run(documents, storage, FakeIndexer(fail=True), apply=True)

    assert code == 1
    assert "[failed]" in out
    assert documents.rows[A].file_path == f"documents/{A}/배포 가이드(v2).md"

    code, _ = _run(documents, storage, FakeIndexer(), apply=True)

    assert code == 0
    assert documents.rows[A].file_path == f"documents/{A}/{A}.md"


def test_missing_source_is_reported_and_left_alone():
    documents, storage = _setup()
    del storage.objects[(BUCKET, f"documents/{A}/배포 가이드(v2).md")]
    indexer = FakeIndexer()

    code, out = _run(documents, storage, indexer, apply=True)

    assert code == 1
    assert f"[source-missing] {A}" in out
    assert indexer.calls == []
    assert documents.rows[A].file_path == f"documents/{A}/배포 가이드(v2).md"
