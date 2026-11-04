"""전환 단계 표(BE 기술문서 3.1절)대로 쓰기 대상, 작업 성공 조건, 재색인 표시가 정해지는지 확인한다.

실행: cd indexing && python -m pytest tests/test_write_targets.py
"""
import pytest

from app.models.schemas import DocumentChunk, FullDocument
from app.search.base import SearchStoreError
from app.search.stores import SearchStores, write_targets
from tests.fakes import MemoryStore

DOC = FullDocument(document_id="d1", title="제목", content="본문", metadata={})
CHUNKS = [DocumentChunk(chunk_id="c1", content="본문", title="제목", metadata={"source_file": "documents/d1/제목.md"})]


@pytest.mark.parametrize("backend, dual, expected", [
    ("es", False, ("es",)),
    ("es", True, ("es", "postgres")),
    ("postgres", True, ("postgres", "es")),
    ("postgres", False, ("postgres",)),
])
def test_write_targets_follow_the_stage_table(backend, dual, expected):
    assert write_targets(backend, dual) == expected


def stores(active, other=None):
    """Mirror build_search_stores: the postgres store also holds the reindex marks."""
    pg = active if active.name == "postgres" else other
    return SearchStores(active=active, others=(other,) if other else (), postgres=pg)


@pytest.mark.parametrize("active_name, other_name", [("es", "postgres"), ("postgres", "es")])
def test_active_failure_fails_the_write_and_skips_the_other_store(active_name, other_name):
    active, other = MemoryStore(active_name, fail_writes=True), MemoryStore(other_name)
    with pytest.raises(SearchStoreError):
        stores(active, other).index_document(DOC, CHUNKS)
    assert other.documents == {}


@pytest.mark.parametrize("active_name, other_name", [("es", "postgres"), ("postgres", "es")])
def test_other_store_failure_keeps_the_write_successful_and_marks_reindex(active_name, other_name):
    active, other = MemoryStore(active_name), MemoryStore(other_name, fail_writes=True)
    s = stores(active, other)
    outcome = s.index_document(DOC, CHUNKS)
    assert outcome[active_name] == "ok" and outcome[other_name].startswith("failed")
    assert "d1" in active.documents
    assert ("d1", other_name) in s.postgres.marks


def test_successful_write_clears_an_earlier_reindex_mark():
    pg, es = MemoryStore("postgres"), MemoryStore("es")
    pg.marks[("d1", "es")] = "earlier failure"
    stores(pg, es).index_document(DOC, CHUNKS)
    assert pg.marks == {}


def test_single_store_stage_writes_only_the_active_store():
    es = MemoryStore("es")
    assert stores(es).index_document(DOC, CHUNKS) == {"es": "ok"}


def test_delete_reports_the_active_store_and_marks_a_failed_other_store():
    pg, es = MemoryStore("postgres"), MemoryStore("es")
    s = stores(pg, es)
    s.index_document(DOC, CHUNKS)
    es.fail_writes = True
    result = s.delete_document("d1")
    assert (result.deleted_chunks, result.deleted_document) == (1, True)
    assert pg.documents == {} and "d1" in es.documents
    assert ("d1", "es") in pg.marks


def test_delete_of_missing_document_succeeds():
    pg = MemoryStore("postgres")
    result = stores(pg).delete_document("missing")
    assert (result.deleted_chunks, result.deleted_document) == (0, False)


def test_delete_fails_when_the_active_store_fails():
    es, pg = MemoryStore("es", fail_writes=True), MemoryStore("postgres")
    with pytest.raises(SearchStoreError):
        stores(es, pg).delete_document("d1")


def test_clear_all_returns_the_active_result_even_if_the_other_store_fails():
    es, pg = MemoryStore("es"), MemoryStore("postgres", fail_writes=True)
    s = stores(es, pg)
    pg.fail_writes = False
    s.index_document(DOC, CHUNKS)
    pg.fail_writes = True
    assert s.clear_all().documents_index == "es_documents"
    assert es.documents == {}


def test_es_only_stage_never_connects_to_postgres():
    from app.config import Settings
    from app.search.stores import build_search_stores
    settings = Settings(_env_file=None, search_backend="es", search_dual_write=False, search_pg_host="invalid.invalid")
    built = build_search_stores(settings)
    assert (built.active.name, built.others, built.postgres) == ("es", (), None)
