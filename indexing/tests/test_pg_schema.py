"""SEARCH_PG_SCHEMA: the search tables live in their own schema while the PGroonga extension stays in public (Supabase).

Run: SEARCH_TEST_PG_PGROONGA=<superuser conninfo of a PGroonga server> python -m pytest tests/test_pg_schema.py
"""
import psycopg
import pytest
from psycopg.conninfo import conninfo_to_dict

from app.config import Settings
from app.search.pg_store import STRATEGIES, PgSearchBackend
from tests.pg_support import CORPUS, SERVERS, TempDatabase, build, make_store

SCHEMA = "docs_portal"
TABLES = {"search_document", "search_chunk", "search_reindex_state"}

pytestmark = pytest.mark.skipif(not SERVERS["pgroonga"], reason="SEARCH_TEST_PG_PGROONGA is not set")


def schema_store(dsn, text_index="pgroonga"):
    params = conninfo_to_dict(dsn)
    settings = Settings(_env_file=None, search_pg_host=params["host"], search_pg_port=int(params.get("port", 5432)),
                        search_pg_db=params["dbname"], search_pg_user=params["user"],
                        search_pg_password=params.get("password", ""), search_pg_text_index=text_index,
                        search_pg_schema=SCHEMA, queue_max_workers=1)
    return PgSearchBackend.from_settings(settings)


def tables(conn, schema):
    return {r[0] for r in conn.execute("select tablename from pg_tables where schemaname = %s", (schema,))}


def indexes(conn, schema):
    return {r[0] for r in conn.execute("select indexname from pg_indexes where schemaname = %s", (schema,))}


@pytest.fixture
def db():
    database = TempDatabase(SERVERS["pgroonga"])
    with psycopg.connect(database.dsn, autocommit=True) as conn:
        conn.execute("create extension pgroonga schema public")
    yield database
    database.drop()


def test_tables_go_to_the_schema_and_search_resolves_the_public_extension(db):
    store = schema_store(db.dsn)
    try:
        store.ensure_schema()
        for doc in CORPUS[:5]:
            store.index_document(*build(doc))
        result = store.search_documents(CORPUS[0]["title"], size=5, from_=0)
        assert CORPUS[0]["document_id"] in [r["document_id"] for r in result["results"]]
        assert store.missing_indexes() == []
    finally:
        store.close()
    with psycopg.connect(db.dsn) as conn:
        assert tables(conn, SCHEMA) == TABLES
        assert tables(conn, "public") == set()
        assert set(STRATEGIES["pgroonga"].indexes) <= indexes(conn, SCHEMA)


def test_switching_strategy_in_the_schema_leaves_public_indexes_alone(db):
    public_store = make_store(db.dsn, "trgm")
    public_store.ensure_schema()
    public_store.close()
    store = schema_store(db.dsn)
    try:
        store.ensure_schema()
        store.ensure_schema()
    finally:
        store.close()
    with psycopg.connect(db.dsn) as conn:
        assert set(STRATEGIES["trgm"].indexes) <= indexes(conn, "public")
        assert tables(conn, "public") == TABLES and tables(conn, SCHEMA) == TABLES
