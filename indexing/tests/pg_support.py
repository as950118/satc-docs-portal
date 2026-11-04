"""Throwaway PostgreSQL databases for the integration tests.

Set SEARCH_TEST_PG_PGROONGA and/or SEARCH_TEST_PG_TRGM to a libpq conninfo of a disposable server, for example
a PGroonga image and the plain postgres:16.3-alpine image. Each test creates its own database on that server
and drops only that database afterwards.
"""
import json
import os
import uuid
from pathlib import Path

import psycopg
import pytest
from psycopg.conninfo import conninfo_to_dict, make_conninfo
from psycopg_pool import ConnectionPool

from app.models.schemas import FullDocument
from app.search.pg_store import PgSearchBackend
from app.services.chunking_service import chunking_service

SERVERS = {name: os.environ.get(f"SEARCH_TEST_PG_{name.upper()}") for name in ("pgroonga", "trgm")}
CORPUS = json.loads((Path(__file__).parent / "fixtures" / "dummy_corpus.json").read_text())["documents"]


def available_strategies():
    return [pytest.param(name, marks=pytest.mark.skipif(not dsn, reason=f"SEARCH_TEST_PG_{name.upper()} is not set"))
            for name, dsn in SERVERS.items()]


class TempDatabase:
    def __init__(self, server_dsn):
        self.server_dsn = server_dsn
        self.name = f"search_test_{uuid.uuid4().hex[:10]}"
        with psycopg.connect(server_dsn, autocommit=True) as conn:
            conn.execute(f'create database "{self.name}"')
        self.dsn = make_conninfo(server_dsn, dbname=self.name)

    def drop(self):
        with psycopg.connect(self.server_dsn, autocommit=True) as conn:
            conn.execute(f'drop database if exists "{self.name}" with (force)')


def make_store(dsn, text_index, threshold="0.2"):
    def configure(conn):
        conn.execute("select set_config('pg_trgm.word_similarity_threshold', %s, false)", (threshold,))
        conn.commit()
    pool = ConnectionPool(dsn, min_size=1, max_size=10, open=True, configure=configure)
    pool.wait(timeout=30)
    return PgSearchBackend(pool, text_index)


def object_name(doc):
    return f"documents/{doc['document_id']}/{doc['title']}.md"


def build(doc, extra_metadata=None):
    """Same metadata keys and chunking as the document processor, without MinIO."""
    name = object_name(doc)
    metadata = {"bucket_name": "dummy-docs", "object_name": name, "document_id": doc["document_id"],
                "document_title": doc["title"], **(extra_metadata or {})}
    chunks = chunking_service.chunk_document(doc["content"], name, metadata)
    return FullDocument(document_id=doc["document_id"], title=doc["title"], content=doc["content"], metadata=metadata), chunks


def server_settings(dsn):
    params = conninfo_to_dict(dsn)
    return {"host": params.get("host"), "port": params.get("port")}
