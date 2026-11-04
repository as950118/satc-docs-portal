"""Which stores answer searches and receive writes, derived from SEARCH_BACKEND and SEARCH_DUAL_WRITE.

| SEARCH_BACKEND | SEARCH_DUAL_WRITE | search   | writes (active first) |
|----------------|-------------------|----------|-----------------------|
| es             | false             | es       | es                    |
| es             | true              | es       | es, postgres          |
| postgres       | true              | postgres | postgres, es          |
| postgres       | false             | postgres | postgres              |

A write must succeed on the active store. A failure on another store only logs a warning and
leaves a mark in search_reindex_state so scripts/reindex_pg.py can repair that store later.
"""
import logging
from dataclasses import dataclass
from typing import Callable, Dict, List, Optional, Tuple

from app.models.schemas import DocumentChunk, FullDocument
from app.search.base import ClearResult, DeleteResult, SearchStore, StoreName

logger = logging.getLogger(__name__)


def write_targets(backend: StoreName, dual_write: bool) -> Tuple[StoreName, ...]:
    other = {"es": "postgres", "postgres": "es"}[backend]
    return (backend, other) if dual_write else (backend,)


@dataclass(frozen=True)
class SearchStores:
    active: SearchStore
    others: Tuple[SearchStore, ...] = ()
    postgres: Optional[SearchStore] = None

    def index_document(self, document: FullDocument, chunks: List[DocumentChunk]) -> Dict[str, str]:
        return self._fan_out(document.document_id, lambda store: store.index_document(document, chunks))

    def delete_document(self, document_id: str) -> DeleteResult:
        results: Dict[str, DeleteResult] = {}
        self._fan_out(document_id, lambda store: results.__setitem__(store.name, store.delete_document(document_id)))
        return results[self.active.name]

    def clear_all(self) -> ClearResult:
        result = self.active.clear_all()
        for store in self.others:
            try:
                store.clear_all()
            except Exception as e:
                logger.warning(f"clear_all failed on {store.name} (active {self.active.name} succeeded): {e}")
        return result

    def _fan_out(self, document_id: str, write: Callable[[SearchStore], None]) -> Dict[str, str]:
        write(self.active)
        self._reindex_mark(document_id, self.active.name, None)
        outcome = {self.active.name: "ok"}
        for store in self.others:
            try:
                write(store)
                outcome[store.name] = "ok"
                self._reindex_mark(document_id, store.name, None)
            except Exception as e:
                logger.warning(f"{store.name} write failed for {document_id} (active {self.active.name} succeeded): {e}")
                outcome[store.name] = f"failed: {e}"
                self._reindex_mark(document_id, store.name, str(e))
        return outcome

    def _reindex_mark(self, document_id: str, target: StoreName, reason: Optional[str]) -> None:
        if self.postgres is None:
            return
        try:
            self.postgres.set_reindex_mark(document_id, target, reason)
        except Exception as e:
            logger.warning(f"could not update reindex mark ({document_id}, {target}, reason={reason!r}): {e}")


_stores: Optional[SearchStores] = None


def build_search_stores(settings) -> SearchStores:
    """PostgreSQL is connected, and its schema ensured, only when it is a write target."""
    from app.search.es_store import EsSearchBackend

    names = write_targets(settings.search_backend, settings.search_dual_write)
    postgres = None
    if "postgres" in names:
        from app.search.pg_store import PgSearchBackend
        postgres = PgSearchBackend.from_settings(settings)
        postgres.ensure_schema()
    stores = {"es": EsSearchBackend(), "postgres": postgres}
    return SearchStores(active=stores[names[0]], others=tuple(stores[n] for n in names[1:]), postgres=postgres)


def set_search_stores(stores: SearchStores) -> None:
    global _stores
    _stores = stores


def get_search_stores() -> SearchStores:
    if _stores is None:
        raise RuntimeError("search stores are not initialised; call set_search_stores at startup")
    return _stores
