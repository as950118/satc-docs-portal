"""병행 쓰기 장애 주입. 실제 PostgreSQL 저장소와 실패하는 ES 대역으로 단계별 작업 성공, 실패, 재색인 표시를 확인한다.

실행: SEARCH_TEST_PG_PGROONGA=... python -m pytest tests/test_dual_write_faults.py
"""
from unittest.mock import MagicMock, patch

import pytest
from psycopg_pool import ConnectionPool

from app.search.pg_store import PgSearchBackend
from app.services.object_storage import StoredObject
from app.search.stores import SearchStores
from tests.fakes import MemoryStore
from tests.pg_support import SERVERS, TempDatabase, make_store

from app.services import document_processor as dp

DOC_ID = "12345678-1234-1234-1234-123456789abc"
OBJECT = f"documents/{DOC_ID}/장애 주입 문서.md"
BODY = "# 장애 주입 문서\n\n병행 쓰기 중 한쪽 저장소가 실패할 때의 동작을 확인하는 더미 문서다. 길이를 채우려고 문장을 하나 더 붙인다."

pytestmark = pytest.mark.skipif(not SERVERS["pgroonga"], reason="SEARCH_TEST_PG_PGROONGA is not set")


@pytest.fixture
def pg():
    db = TempDatabase(SERVERS["pgroonga"])
    store = make_store(db.dsn, "pgroonga")
    store.ensure_schema()
    yield store
    store.close()
    db.drop()


@pytest.fixture
def unreachable_pg():
    store = PgSearchBackend(ConnectionPool("host=127.0.0.1 port=1 user=x dbname=x connect_timeout=1",
                                          min_size=1, open=True, timeout=1, name="unreachable"), "pgroonga")
    yield store
    store.pool.close(timeout=0)


def process(stores):
    task = MagicMock(task_id="t1", data={"bucket_name": "dummy-docs", "object_name": OBJECT, "metadata": {}})
    with patch.object(dp, "get_queue_service", return_value=MagicMock()), \
         patch.object(dp, "object_storage") as storage, \
         patch.object(dp, "get_search_stores", return_value=stores), \
         patch.object(dp, "update_document_status", return_value=True) as status:
        storage.get.return_value = StoredObject(data=BODY.encode("utf-8"), content_type="text/markdown", last_modified=None)
        try:
            dp.process_document_handler(task)
        except Exception:
            pass
        return status.call_args.args[1]


def test_switched_stage_with_es_down_completes_on_postgres_and_marks_es(pg):
    es = MemoryStore("es", fail_writes=True)
    assert process(SearchStores(active=pg, others=(es,), postgres=pg)) == "COMPLETE"
    assert [r["document_id"] for r in pg.search_documents("장애 주입", 10, 0)["results"]] == [DOC_ID]
    assert pg.stats()["reindex_marks"] == {"es": 1}


def test_switched_stage_clears_the_es_mark_once_es_recovers(pg):
    es = MemoryStore("es", fail_writes=True)
    stores = SearchStores(active=pg, others=(es,), postgres=pg)
    process(stores)
    es.fail_writes = False
    assert process(stores) == "COMPLETE"
    assert pg.stats()["reindex_marks"] == {} and DOC_ID in es.documents


def test_parallel_stage_with_postgres_down_still_completes_on_es(unreachable_pg):
    es = MemoryStore("es")
    assert process(SearchStores(active=es, others=(unreachable_pg,), postgres=unreachable_pg)) == "COMPLETE"
    assert DOC_ID in es.documents


def test_switched_stage_with_postgres_down_fails_and_skips_es(unreachable_pg):
    es = MemoryStore("es")
    assert process(SearchStores(active=unreachable_pg, others=(es,), postgres=unreachable_pg)) == "FAILED"
    assert es.documents == {}
