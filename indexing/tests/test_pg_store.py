"""PgSearchBackend 를 실제 PostgreSQL(PGroonga 이미지, pg_trgm 만 있는 기본 이미지)에서 확인한다.

실행: SEARCH_TEST_PG_PGROONGA='host=127.0.0.1 port=55441 user=postgres password=tmp dbname=postgres' \
      SEARCH_TEST_PG_TRGM='host=127.0.0.1 port=55442 user=postgres password=tmp dbname=postgres' \
      python -m pytest tests/test_pg_store.py
"""
import re
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import pytest
import yaml

from app.models.schemas import DocumentChunk, FullDocument
from app.search.base import SearchStoreError
from app.search.pg_store import pgroonga_terms
from tests.pg_support import CORPUS, SERVERS, TempDatabase, available_strategies, build, make_store

BY_TITLE = {d["title"]: d for d in CORPUS}


@pytest.fixture(params=available_strategies())
def store(request):
    db = TempDatabase(SERVERS[request.param])
    s = make_store(db.dsn, request.param)
    s.ensure_schema()
    yield s
    s.close()
    db.drop()


def load(store, titles=None):
    for doc in CORPUS:
        if titles is None or doc["title"] in titles:
            store.index_document(*build(doc))


def counts(store):
    with store.pool.connection() as conn:
        return conn.execute("select (select count(*) from search_document), (select count(*) from search_chunk)").fetchone()


def ids(result):
    return [r["document_id"] for r in result["results"]]


def test_schema_is_idempotent_and_keeps_rows(store):
    load(store, {"커밋 메시지 형식", "검색 품질 점검 기록"})
    before = counts(store)
    store.ensure_schema()
    store.ensure_schema()
    assert counts(store) == before
    assert store.missing_indexes() == []


def test_pgroonga_terms_add_a_stem_only_for_hangul_final_terms():
    assert pgroonga_terms("코딩 스타일을 readOnly 롬복") == ["코딩", "스타일을", "스타일", "readOnly", "롬복"]


def test_query_with_a_particle_finds_the_same_document_as_without(store):
    load(store)
    pairs = defaultdict(list)
    for q in yaml.safe_load((Path(__file__).parents[1] / "scripts/querysets/dummy_ko.yaml").read_text())["queries"]:
        if q["pair_id"]:
            pairs[q["pair_id"]].append(q)
    assert pairs
    for members in pairs.values():
        for q in members:
            top5 = ids(store.search_documents(q["query"], 5, 0))
            assert set(top5) & set(q["expected_doc_ids"]), q["query"]


def test_indexed_document_is_found_by_its_id_and_gone_after_delete(store):
    doc = BY_TITLE["커밋 메시지 형식"]
    load(store, {doc["title"]})
    assert ids(store.search_documents("커밋 메시지", 10, 0)) == [doc["document_id"]]
    deleted = store.delete_document(doc["document_id"])
    assert deleted.deleted_document and deleted.deleted_chunks == 1
    assert store.search_documents("커밋 메시지", 10, 0)["results"] == []
    again = store.delete_document(doc["document_id"])
    assert (again.deleted_chunks, again.deleted_document) == (0, False)


def test_reindexing_the_same_input_does_not_add_rows(store):
    load(store)
    first = counts(store)
    load(store)
    assert counts(store) == first == (len(CORPUS), first[1])


def test_reindex_with_no_chunks_removes_old_chunks(store):
    doc = BY_TITLE["검색 품질 점검 기록"]
    load(store, {doc["title"]})
    full, _ = build(doc)
    store.index_document(full, [])
    assert counts(store) == (1, 0)


def test_empty_body_is_found_only_by_title_with_empty_excerpts(store):
    load(store, {"빈 본문 안내", "괄호와 들여쓰기 스타일"})
    empty_id = BY_TITLE["빈 본문 안내"]["document_id"]
    assert empty_id not in ids(store.search_documents("들여쓰기", 10, 0))
    hit = next(r for r in store.search_documents("본문 안내", 10, 0)["results"] if r["document_id"] == empty_id)
    assert "content" not in hit["highlight"]
    assert hit["highlight"]["title"] == ["빈 <em>본문</em> <em>안내</em>"]


def test_title_only_match_returns_document_with_empty_excerpts(store):
    load(store, {"온보딩 체크리스트"})
    [hit] = store.search_documents("온보딩", 10, 0)["results"]
    assert hit["highlight"] == {"title": ["<em>온보딩</em> 체크리스트"]}


def test_document_excerpts_follow_the_portal_rule(store):
    load(store)
    for q in ("점검", "발췌", "코딩 스타일을", "굵은 글씨"):
        for r in store.search_documents(q, 10, 0)["results"]:
            fragments = r["highlight"].get("content", [])
            assert len(fragments) <= 3
            assert all(len(re.sub(r"</?em>", "", f).replace("&lt;", "<").replace("&gt;", ">")
                           .replace("&amp;", "&").replace("&quot;", '"').replace("&#x27;", "'")) <= 150 for f in fragments)
    html = store.search_documents("굵은 글씨", 10, 0)["results"]
    assert any("&lt;b&gt;" in f for r in html for f in r["highlight"].get("content", []))


@pytest.mark.parametrize("q", ["(괄호", "\"따옴표", "OR", "a OR OR b", "-x", "N+1", "a:b", "\\", "'", "*", "~", "<>", "  "])
def test_query_syntax_characters_do_not_fail(store, q):
    load(store, {"N+1 문제 해결", "HTML 이스케이프 확인"})
    store.search_documents(q, 10, 0)
    store.search_chunks(q, 10, None)


def test_chunk_search_filters_by_source_file(store):
    load(store)
    source = "documents/eeeeeeee-0000-4000-8000-000000000004/검색 품질 점검 기록.md"
    filtered = store.search_chunks("점검", 100, source)
    assert filtered["results"] and {r["metadata"]["source_file"] for r in filtered["results"]} == {source}
    everything = store.search_chunks("점검", 100, None)
    assert everything["total_results"] > filtered["total_results"]
    assert all(len(r["highlight"].get("content", [])) <= 5 for r in everything["results"])


def test_paging_beyond_the_results_keeps_total_and_max_score(store):
    load(store)
    first = store.search_documents("브랜치", 2, 0)
    beyond = store.search_documents("브랜치", 2, 1000)
    assert beyond["results"] == [] and beyond["total_results"] == first["total_results"] > 0
    assert beyond["max_score"] == first["max_score"]


def test_no_hit_has_null_max_score(store):
    load(store, {"커밋 메시지 형식"})
    assert store.search_documents("zzqxj없는말", 10, 0) == {"total_results": 0, "returned_results": 0, "max_score": None, "results": []}


def test_get_document_returns_the_es_document_shape(store):
    doc = BY_TITLE["검색 품질 점검 기록"]
    load(store, {doc["title"]})
    got = store.get_document(doc["document_id"])
    assert list(got) == ["document_id", "title", "content", "metadata", "created_at", "updated_at"]
    assert got["content"] == doc["content"] and got["metadata"]["object_name"].endswith(".md")
    assert store.get_document("ffffffff-0000-4000-8000-000000000000") is None


def test_concurrent_writes_of_one_document_leave_one_version(store):
    doc = BY_TITLE["검색 품질 점검 기록"]
    with ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(lambda _: store.index_document(*build(doc)), range(16)))
    _, chunks = build(doc)
    assert counts(store) == (1, len(chunks))


def test_result_document_replaces_other_versions_of_its_feature(store):
    versions = [{"document_id": f"aaaaaaaa-0000-4000-8000-{n:012d}", "title": f"결과 문서 v{n}",
                 "content": f"# 결과 문서 v{n}\n\n기능 F-1 의 개발 결과를 정리한 더미 문서이고 버전 {n} 의 내용을 담는다. " * 3}
                for n in range(1, 7)]
    meta = {"doc_type": "result", "feature_id": "F-1"}
    with ThreadPoolExecutor(max_workers=6) as pool:
        list(pool.map(lambda d: store.index_document(*build(d, meta)), versions))
    with store.pool.connection() as conn:
        docs = conn.execute("select document_id from search_document where metadata->>'feature_id' = 'F-1'").fetchall()
        orphan = conn.execute("select count(*) from search_chunk c left join search_document d using (document_id) "
                              "where d.document_id is null").fetchone()[0]
    assert len(docs) == 1 and orphan == 0


def test_reindex_marks_are_set_and_cleared(store):
    store.set_reindex_mark("d1", "es", "es down")
    store.set_reindex_mark("d1", "es", "es down again")
    assert store.stats()["reindex_marks"] == {"es": 1}
    store.set_reindex_mark("d1", "es", None)
    assert store.stats()["reindex_marks"] == {}


def test_nul_characters_do_not_fail_the_write(store):
    store.index_document(FullDocument(document_id="nul", title="널\x00문자", content="본문\x00내용", metadata={}),
                         [DocumentChunk(chunk_id="nul-0", content="본문\x00내용", title="널", metadata={"source_file": "x"})])
    assert store.get_document("nul")["content"] == "본문내용"


def test_missing_extension_refuses_with_a_hint():
    if not SERVERS["trgm"]:
        pytest.skip("SEARCH_TEST_PG_TRGM is not set")
    db = TempDatabase(SERVERS["trgm"])
    s = make_store(db.dsn, "pgroonga")
    try:
        with pytest.raises(SearchStoreError, match="SEARCH_PG_TEXT_INDEX=trgm"):
            s.ensure_schema()
    finally:
        s.close()
        db.drop()
