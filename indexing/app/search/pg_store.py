"""PostgreSQL store. search_document and search_chunk mirror the ES full_documents and document_chunks indexes.

The text strategy (SEARCH_PG_TEXT_INDEX) decides the match condition and the score:
- pgroonga: each whitespace-separated query term is escaped with pgroonga_query_escape and the terms are
  joined with OR, matched per column (title &@~ q or content &@~ q). This column-wise form uses the
  pgroonga index; the weighted ARRAY[...] &@~ form fell back to a sequential scan in the PoC.
  The bigram index matches a term only as a whole, so "스타일을" misses a text with "스타일은". A term of three
  or more characters that ends in a Hangul syllable therefore also searches its stem without the last
  character (see pgroonga_terms). On the dummy query sets this took the particle pairs from 0/4 to 4/4.
- trgm: q <% title or q <% content, ranked by 3 * word_similarity(title) + word_similarity(content).
"""
import logging
from dataclasses import dataclass
from datetime import timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

from psycopg.conninfo import make_conninfo
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb
from psycopg_pool import ConnectionPool

from app.models.schemas import DocumentChunk, FullDocument
from app.search.base import ClearResult, DeleteResult, SearchStoreError
from app.search.highlight import excerpts, highlight_whole, query_terms

logger = logging.getLogger(__name__)

SCHEMA_DIR = Path(__file__).resolve().parents[1] / "db"

DOCUMENT_FRAGMENTS = (150, 3)
# ES default highlight for the chunk search, measured on a local ES 8.11 (tests/fixtures/es_search_snapshot.json).
CHUNK_FRAGMENTS = (100, 5)

PGROONGA_QUERY = "(select string_agg(pgroonga_query_escape(t), ' OR ') from unnest(%(terms)s::text[]) as t)"


@dataclass(frozen=True)
class TextStrategy:
    extension: str
    schema_file: str
    indexes: tuple
    match: str
    score: str


STRATEGIES = {
    "pgroonga": TextStrategy(
        extension="pgroonga",
        schema_file="schema_pgroonga.sql",
        indexes=("idx_search_document_pgroonga", "idx_search_chunk_pgroonga"),
        match=f"(t.title &@~ {PGROONGA_QUERY} or t.content &@~ {PGROONGA_QUERY})",
        score="pgroonga_score(t.tableoid, t.ctid)",
    ),
    "trgm": TextStrategy(
        extension="pg_trgm",
        schema_file="schema_trgm.sql",
        indexes=("idx_search_document_title_trgm", "idx_search_document_content_trgm",
                 "idx_search_chunk_title_trgm", "idx_search_chunk_content_trgm"),
        match="(%(q)s <%% t.title or %(q)s <%% t.content)",
        score="(3 * word_similarity(%(q)s, t.title) + word_similarity(%(q)s, t.content))",
    ),
}

FALLBACK_HINT = {"pgroonga": "PGroonga 포함 이미지(docker/postgres-pgroonga)를 쓰거나 SEARCH_PG_TEXT_INDEX=trgm 으로 설정하세요.",
                 "trgm": "pg_trgm 확장을 설치하세요."}


def pgroonga_terms(query: str) -> List[str]:
    terms = []
    for term in query_terms(query):
        terms.append(term)
        if len(term) >= 3 and "\uac00" <= term[-1] <= "\ud7a3":
            terms.append(term[:-1])
    return terms


def _text(value: str) -> str:
    # PostgreSQL text cannot hold NUL, which a latin-1 decoded binary upload can contain.
    return value.replace("\x00", "")


def _iso(ts) -> str:
    """Same shape as the ES documents: naive UTC isoformat."""
    return ts.astimezone(timezone.utc).replace(tzinfo=None).isoformat()


def _result_feature_id(metadata: Dict[str, Any]) -> Optional[str]:
    if metadata.get("doc_type") == "result" and metadata.get("feature_id"):
        return str(metadata["feature_id"])
    return None


class PgSearchBackend:
    name = "postgres"

    def __init__(self, pool: ConnectionPool, text_index: str):
        self.pool = pool
        self.text_index = text_index
        self.strategy = STRATEGIES[text_index]

    @classmethod
    def from_settings(cls, settings) -> "PgSearchBackend":
        threshold = str(settings.search_pg_trgm_threshold)

        def configure(conn):
            conn.execute("select set_config('pg_trgm.word_similarity_threshold', %s, false)", (threshold,))
            conn.commit()

        pool = ConnectionPool(cls.conninfo(settings), min_size=1, max_size=settings.queue_max_workers + 2, open=True,
                              configure=configure if settings.search_pg_text_index == "trgm" else None,
                              name="search-pg")
        pool.wait(timeout=30)
        return cls(pool, settings.search_pg_text_index)

    @staticmethod
    def conninfo(settings) -> str:
        return make_conninfo(host=settings.search_pg_host, port=settings.search_pg_port, dbname=settings.search_pg_db,
                             user=settings.search_pg_user, password=settings.search_pg_password)

    def close(self) -> None:
        self.pool.close()

    def ensure_schema(self) -> None:
        """Create the tables and the strategy's indexes. Refuse when the strategy's extension is missing."""
        with self.pool.connection() as conn, conn.transaction():
            # Two indexing processes starting together would race on CREATE TABLE IF NOT EXISTS.
            conn.execute("select pg_advisory_xact_lock(hashtext('satc-search-schema'))")
            available = conn.execute("select 1 from pg_available_extensions where name = %s",
                                     (self.strategy.extension,)).fetchone()
            if not available:
                raise SearchStoreError(f"SEARCH_PG_TEXT_INDEX={self.text_index} 인데 PostgreSQL 에 {self.strategy.extension} "
                                       f"확장이 없습니다. {FALLBACK_HINT[self.text_index]}")
            conn.execute((SCHEMA_DIR / "schema.sql").read_text())
            conn.execute((SCHEMA_DIR / self.strategy.schema_file).read_text())
        logger.info(f"search schema ready (text index: {self.text_index})")

    def missing_indexes(self) -> List[str]:
        """Text indexes of the configured strategy that do not exist. Measuring tools check this instead of
        running ensure_schema, which would drop the indexes of a service running the other strategy."""
        with self.pool.connection() as conn:
            present = {r[0] for r in conn.execute("select indexname from pg_indexes where indexname = any(%s)",
                                                  (list(self.strategy.indexes),))}
        return [name for name in self.strategy.indexes if name not in present]

    # ---- writes ----

    def index_document(self, document: FullDocument, chunks: List[DocumentChunk]) -> None:
        """One transaction per document: upsert the document, then replace all of its chunks."""
        doc_id = document.document_id
        feature_id = _result_feature_id(document.metadata)
        with self.pool.connection() as conn, conn.transaction():
            if feature_id:
                # Without the lock two versions of one feature written together would not see each other and both stay.
                conn.execute("select pg_advisory_xact_lock(hashtext('feature:' || %s))", (feature_id,))
            conn.execute(
                "insert into search_document (document_id, title, content, metadata) values (%s, %s, %s, %s) "
                "on conflict (document_id) do update set title = excluded.title, content = excluded.content, "
                "metadata = excluded.metadata, updated_at = now()",
                (doc_id, _text(document.title), _text(document.content), Jsonb(document.metadata)))
            if feature_id:
                conn.execute(
                    "with stale as (delete from search_document where metadata->>'doc_type' = 'result' "
                    "and metadata->>'feature_id' = %s and document_id <> %s returning document_id) "
                    "delete from search_chunk where document_id in (select document_id from stale)",
                    (feature_id, doc_id))
            conn.execute("delete from search_chunk where document_id = %s", (doc_id,))
            with conn.cursor() as cur:
                cur.executemany(
                    "insert into search_chunk (chunk_id, document_id, source_file, title, content, metadata) "
                    "values (%s, %s, %s, %s, %s, %s)",
                    [(c.chunk_id, doc_id, c.metadata["source_file"], _text(c.title), _text(c.content), Jsonb(c.metadata))
                     for c in chunks])

    def delete_document(self, document_id: str) -> DeleteResult:
        with self.pool.connection() as conn:
            chunks, documents = conn.execute(
                "with c as (delete from search_chunk where document_id = %(id)s returning 1), "
                "d as (delete from search_document where document_id = %(id)s returning 1) "
                "select (select count(*) from c), (select count(*) from d)", {"id": document_id}).fetchone()
        return DeleteResult(deleted_chunks=chunks, deleted_document=documents > 0)

    def clear_all(self) -> ClearResult:
        with self.pool.connection() as conn:
            chunks, documents = conn.execute(
                "with c as (delete from search_chunk returning 1), d as (delete from search_document returning 1) "
                "select (select count(*) from c), (select count(*) from d)").fetchone()
        return ClearResult(deleted_chunks=chunks, deleted_documents=documents,
                           chunks_index="search_chunk", documents_index="search_document")

    def set_reindex_mark(self, document_id: str, target: str, reason: Optional[str]) -> None:
        with self.pool.connection() as conn:
            if reason is None:
                conn.execute("delete from search_reindex_state where document_id = %s and target = %s", (document_id, target))
            else:
                conn.execute(
                    "insert into search_reindex_state (document_id, target, reason) values (%s, %s, %s) "
                    "on conflict (document_id, target) do update set reason = excluded.reason, marked_at = now()",
                    (document_id, target, reason))

    # ---- reads ----

    def _ranked(self, table: str, columns: str, query: str, size: int, offset: int, extra_where: str = "",
                extra_params: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        terms = query_terms(query)
        if not terms:
            return {"total": 0, "max_score": None, "rows": []}
        params = {"q": query, "terms": pgroonga_terms(query), "size": size, "offset": offset, **(extra_params or {})}
        where = f"{self.strategy.match}{extra_where}"
        sql = (f"select {columns}, {self.strategy.score} as score, "
               f"count(*) over () as total, max({self.strategy.score}) over () as top "
               f"from {table} t where {where} order by score desc, 1 limit %(size)s offset %(offset)s")
        with self.pool.connection() as conn, conn.cursor(row_factory=dict_row) as cur:
            rows = cur.execute(sql, params).fetchall()
            if rows:
                return {"total": rows[0]["total"], "max_score": float(rows[0]["top"]), "rows": rows}
            if offset == 0:
                return {"total": 0, "max_score": None, "rows": []}
            count = cur.execute(f"select count(*) as total, max({self.strategy.score}) as top from {table} t where {where}",
                                params).fetchone()
            return {"total": count["total"], "max_score": float(count["top"]) if count["top"] is not None else None, "rows": []}

    def search_documents(self, query: str, size: int, from_: int) -> Dict[str, Any]:
        ranked = self._ranked("search_document", "t.document_id, t.title, t.content, t.metadata", query, size, from_)
        results = []
        for row in ranked["rows"]:
            highlight = {}
            title = highlight_whole(row["title"], query)
            if title is not None:
                highlight["title"] = [title]
            content = excerpts(row["content"], query, *DOCUMENT_FRAGMENTS)
            if content:
                highlight["content"] = content
            results.append({"document_id": row["document_id"], "title": row["title"], "score": float(row["score"]),
                            "metadata": row["metadata"], "highlight": highlight})
        return {"total_results": ranked["total"], "returned_results": len(results),
                "max_score": ranked["max_score"], "results": results}

    def search_chunks(self, query: str, size: int, source_file: Optional[str]) -> Dict[str, Any]:
        extra = " and t.source_file = %(source_file)s" if source_file else ""
        ranked = self._ranked("search_chunk", "t.chunk_id, t.content, t.title, t.metadata", query, size, 0,
                              extra, {"source_file": source_file})
        results = []
        for row in ranked["rows"]:
            highlight = {}
            content = excerpts(row["content"], query, *CHUNK_FRAGMENTS)
            if content:
                highlight["content"] = content
            title = highlight_whole(row["title"], query)
            if title is not None:
                highlight["title"] = [title]
            results.append({"chunk_id": row["chunk_id"], "score": float(row["score"]), "content": row["content"],
                            "title": row["title"], "metadata": row["metadata"], "highlight": highlight})
        return {"total_results": ranked["total"], "returned_results": len(results),
                "max_score": ranked["max_score"], "results": results}

    def get_document(self, document_id: str) -> Optional[Dict[str, Any]]:
        with self.pool.connection() as conn, conn.cursor(row_factory=dict_row) as cur:
            row = cur.execute("select document_id, title, content, metadata, created_at, updated_at "
                               "from search_document where document_id = %s", (document_id,)).fetchone()
        if row is None:
            return None
        return {**row, "created_at": _iso(row["created_at"]), "updated_at": _iso(row["updated_at"])}

    def stats(self) -> Dict[str, Any]:
        try:
            with self.pool.connection() as conn:
                documents, chunks = conn.execute(
                    "select (select count(*) from search_document), (select count(*) from search_chunk)").fetchone()
                marks = dict(conn.execute("select target, count(*) from search_reindex_state group by 1").fetchall())
            return {"document_count": documents, "chunk_count": chunks, "reindex_marks": marks, "text_index": self.text_index}
        except Exception as e:
            logger.error(f"Error getting postgres search stats: {e}")
            return {"error": str(e)}
