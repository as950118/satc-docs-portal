"""postgres 백엔드의 API 응답이 ES 기준 스냅샷(tests/fixtures/es_search_snapshot.json)과 같은 필드 구성인지 확인한다 (AC-3, AC-6).

같은 더미 코퍼스를 임시 PostgreSQL 에 색인하고 스냅샷과 같은 호출을 API 로 보낸다.
실행: SEARCH_TEST_PG_PGROONGA=... SEARCH_TEST_PG_TRGM=... python -m pytest tests/test_search_contract.py
"""
import json
import re
import sys
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest
from fastapi.testclient import TestClient

from app.search.stores import SearchStores, set_search_stores
from tests.pg_support import CORPUS, SERVERS, TempDatabase, available_strategies, build, make_store

# minio_client connects to MinIO on import; the search routes never use it.
with patch.dict(sys.modules, {"app.services.minio_client": MagicMock()}):
    from app.main import app
    from scripts.search_api_snapshot import CALLS, LONG_DOC_SOURCE

ES = json.loads((Path(__file__).parent / "fixtures" / "es_search_snapshot.json").read_text())["responses"]


@pytest.fixture(scope="module", params=available_strategies())
def client(request):
    db = TempDatabase(SERVERS[request.param])
    store = make_store(db.dsn, request.param)
    store.ensure_schema()
    for doc in CORPUS:
        store.index_document(*build(doc))
    set_search_stores(SearchStores(active=store, postgres=store))
    yield TestClient(app)
    store.close()
    db.drop()


def check_result_shape(es_item, pg_item):
    assert list(pg_item) == list(es_item)
    assert isinstance(pg_item["score"], float)
    assert set(pg_item["highlight"]) <= {"title", "content"}
    assert all(isinstance(f, str) for fragments in pg_item["highlight"].values() for f in fragments)


@pytest.mark.parametrize("name, path, params", CALLS, ids=[c[0] for c in CALLS])
def test_response_fields_match_the_es_snapshot(client, name, path, params):
    es = ES[name]
    r = client.get(path, params=params)
    assert r.status_code == es["status"]
    body = r.json()
    assert list(body) == list(es["body"])
    if "results" not in body:
        return
    assert (body["max_score"] is None) == (es["body"]["max_score"] is None)
    template = es["body"]["results"][0] if es["body"]["results"] else None
    for item in body["results"]:
        check_result_shape(template or item, item)


def plain_len(fragment):
    return len(re.sub(r"&(lt|gt|amp|quot|#x27);", "_", re.sub(r"</?em>", "", fragment)))


def test_portal_excerpts_are_at_most_three_of_150_characters(client):
    for q in ("점검", "발췌", "코딩 스타일을", "커밋 메시지 제목 길이"):
        for item in client.get("/api/v1/search-documents", params={"q": q}).json()["results"]:
            fragments = item["highlight"].get("content", [])
            assert len(fragments) <= 3 and all(plain_len(f) <= 150 and "<em>" in f for f in fragments)


def test_title_only_match_has_no_content_excerpt(client):
    [item] = client.get("/api/v1/search-documents", params={"q": "온보딩"}).json()["results"]
    assert "content" not in item["highlight"]


def test_chunk_search_with_source_file_returns_only_that_file(client):
    r = client.get("/api/v1/search", params={"q": "점검", "source_file": LONG_DOC_SOURCE})
    assert r.status_code == 200
    results = r.json()["results"]
    assert results and {x["metadata"]["source_file"] for x in results} == {LONG_DOC_SOURCE}


def test_stats_keeps_the_es_key_and_adds_postgres(client):
    from app.api import routes
    with patch.object(routes, "elasticsearch_client") as es:
        es.get_index_stats.return_value = {"document_count": 0}
        body = client.get("/api/v1/stats").json()
    assert {"elasticsearch", "queue", "message"} <= set(body)
    assert body["postgres"]["document_count"] == len(CORPUS)
