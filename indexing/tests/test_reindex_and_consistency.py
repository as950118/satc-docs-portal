"""재색인(AC-5)과 정합성 검사를 실제 PostgreSQL 에서 확인한다. 원본은 MinIO 대신 더미 코퍼스에서 읽는다.

실행: SEARCH_TEST_PG_PGROONGA=... python -m pytest tests/test_reindex_and_consistency.py
"""
import json

import psycopg
import pytest
from psycopg.conninfo import conninfo_to_dict

from app.config import settings
from scripts import check_search_consistency as consistency
from scripts import reindex_pg
from tests.pg_support import CORPUS, SERVERS, TempDatabase, build, make_store, object_name

pytestmark = pytest.mark.skipif(not SERVERS["pgroonga"], reason="SEARCH_TEST_PG_PGROONGA is not set")

DELETED = CORPUS[0]
ACTIVE = CORPUS[1:]
BY_PATH = {object_name(d): d for d in CORPUS}


@pytest.fixture
def env():
    db = TempDatabase(SERVERS["pgroonga"])
    store = make_store(db.dsn, "pgroonga")
    store.ensure_schema()
    conn = psycopg.connect(db.dsn, autocommit=True)
    conn.execute("create table document (id varchar(255) primary key, bucket varchar(255), file_path varchar(255), "
                 "title varchar(255), is_deleted boolean, is_folder boolean)")
    for d in CORPUS:
        conn.execute("insert into document values (%s, 'dummy-docs', %s, %s, %s, false)",
                     (d["document_id"], object_name(d), d["title"], d is DELETED))
    conn.execute("insert into document values ('folder-1', null, null, '폴더', false, true)")
    yield db, store, conn
    conn.close()
    store.close()
    db.drop()


def builder(fail_on=None):
    def build_from_corpus(bucket, path):
        if fail_on and path == object_name(fail_on):
            raise RuntimeError("download failed")
        return build(BY_PATH[path])
    return build_from_corpus


def run(conn, store, build=None, **kw):
    options = {"batch_size": 7, "from_id": None, "from_marks": False, **kw}
    return reindex_pg.reindex(conn, store, store, build or builder(), report=reindex_pg.Report(target=store.name), **options)


def test_full_reindex_indexes_every_active_document_and_passes_the_check(env):
    _, store, conn = env
    report = run(conn, store)
    assert (report.targets, report.succeeded, report.failures) == (len(ACTIVE), len(ACTIVE), [])
    result = consistency.check_postgres(conn)
    assert result["targets"] == result["indexed_targets"] == result["indexed_all"] == len(ACTIVE)
    assert consistency.failed(result) == []


def test_second_run_keeps_document_and_chunk_counts(env):
    _, store, conn = env
    run(conn, store)
    first = consistency.check_postgres(conn)
    run(conn, store)
    second = consistency.check_postgres(conn)
    assert (second["indexed_all"], second["chunks"]) == (first["indexed_all"], first["chunks"])


def test_from_id_resumes_after_the_given_id(env):
    _, store, conn = env
    ids = sorted(d["document_id"] for d in ACTIVE)
    report = run(conn, store, from_id=ids[9])
    assert report.targets == len(ids) - 10


def test_one_failure_is_listed_and_the_rest_continue(env):
    _, store, conn = env
    report = run(conn, store, build=builder(fail_on=ACTIVE[3]))
    assert [doc_id for doc_id, _ in report.failures] == [ACTIVE[3]["document_id"]]
    assert report.succeeded == len(ACTIVE) - 1
    assert consistency.failed(consistency.check_postgres(conn)) == ["targets != indexed_targets"]


def test_deleted_document_left_in_the_index_fails_the_check_and_marks_repair_it(env):
    _, store, conn = env
    run(conn, store)
    store.index_document(*build(DELETED))
    assert consistency.failed(consistency.check_postgres(conn)) == ["deleted_still_indexed"]
    store.set_reindex_mark(DELETED["document_id"], "postgres", "delete failed")
    store.set_reindex_mark(ACTIVE[0]["document_id"], "postgres", "write failed")
    report = run(conn, store, from_marks=True)
    assert (report.targets, report.removed, report.succeeded) == (2, 1, 1)
    result = consistency.check_postgres(conn)
    assert consistency.failed(result) == [] and result["reindex_marks"] == {}


def test_index_only_documents_are_copied_without_chunks(env):
    _, store, conn = env
    report = reindex_pg.Report(target="postgres")
    es_docs = [{"document_id": "index-only-1", "title": "색인 전용", "content": "포털 행이 없는 더미 문서", "metadata": {}},
               {"document_id": ACTIVE[0]["document_id"], "title": "x", "content": "x", "metadata": {}}]
    reindex_pg.copy_index_only(conn, iter(es_docs), store, report)
    assert report.index_only_copied == 1
    assert consistency.check_postgres(conn)["index_only"] == 1


def test_dry_run_reports_targets_and_writes_nothing(env, monkeypatch, capsys):
    db, _, conn = env
    params = conninfo_to_dict(db.dsn)
    for key, value in {"search_pg_host": params["host"], "search_pg_port": int(params["port"]), "search_pg_db": db.name,
                       "search_pg_user": params["user"], "search_pg_password": params["password"],
                       "search_pg_text_index": "pgroonga"}.items():
        monkeypatch.setattr(settings, key, value)
    assert reindex_pg.main(["--dry-run"]) == 0
    out = json.loads(capsys.readouterr().out)
    assert out["targets"] == len(ACTIVE) and len(out["first_ids"]) == 10
    assert consistency.check_postgres(conn)["indexed_all"] == 0
