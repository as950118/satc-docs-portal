"""Shape shared by the search stores. Search methods return the API payload without the "query" key."""
from dataclasses import dataclass
from typing import Any, Dict, List, Literal, Optional, Protocol

from app.models.schemas import DocumentChunk, FullDocument

StoreName = Literal["es", "postgres"]


class SearchStoreError(Exception):
    """A store could not complete a read or write."""


@dataclass(frozen=True)
class DeleteResult:
    deleted_chunks: int
    deleted_document: bool


@dataclass(frozen=True)
class ClearResult:
    deleted_chunks: int
    deleted_documents: int
    chunks_index: str
    documents_index: str


class SearchStore(Protocol):
    name: StoreName

    def search_documents(self, query: str, size: int, from_: int) -> Dict[str, Any]: ...

    def search_chunks(self, query: str, size: int, source_file: Optional[str]) -> Dict[str, Any]: ...

    def get_document(self, document_id: str) -> Optional[Dict[str, Any]]: ...

    def index_document(self, document: FullDocument, chunks: List[DocumentChunk]) -> None: ...

    def delete_document(self, document_id: str) -> DeleteResult: ...

    def clear_all(self) -> ClearResult: ...

    def stats(self) -> Dict[str, Any]: ...
