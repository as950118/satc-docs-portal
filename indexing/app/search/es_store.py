"""Elasticsearch store. It keeps the existing ES client behavior and turns their error dicts into SearchStoreError."""
import logging
from typing import Any, Dict, List, Optional

from app.models.schemas import DocumentChunk, FullDocument
from app.search.base import ClearResult, DeleteResult, SearchStoreError
from app.services.elasticsearch_client import elasticsearch_client
from app.services.full_document_client import full_document_client

logger = logging.getLogger(__name__)


class EsSearchBackend:
    name = "es"

    def search_documents(self, query: str, size: int, from_: int) -> Dict[str, Any]:
        result = full_document_client.search_documents(query=query, size=size, from_=from_)
        if "error" in result:
            raise SearchStoreError(result["error"])
        return result

    def search_chunks(self, query: str, size: int, source_file: Optional[str]) -> Dict[str, Any]:
        result = elasticsearch_client.search_chunks(query=query, size=size, source_file=source_file)
        if "error" in result:
            raise SearchStoreError(result["error"])
        return {"total_results": result["total"], "returned_results": len(result["results"]),
                "max_score": result["max_score"], "results": result["results"]}

    def get_document(self, document_id: str) -> Optional[Dict[str, Any]]:
        return full_document_client.get_document(document_id)

    def index_document(self, document: FullDocument, chunks: List[DocumentChunk]) -> None:
        # A document without chunks keeps its old chunks in ES, as before this store existed.
        if not chunks:
            full = full_document_client.index_document(document)
            if not full.get("success"):
                raise SearchStoreError(f"full document indexing failed: {full.get('error')}")
            return
        elasticsearch_client.delete_chunks_by_source(chunks[0].metadata["source_file"])
        bulk = elasticsearch_client.index_chunks_bulk(chunks)
        full = full_document_client.index_document(document)
        if not full.get("success"):
            logger.warning(f"ES full document indexing failed for {document.document_id}: {full.get('error')}")
        if bulk["success"] == 0:
            raise SearchStoreError(f"chunk indexing failed: {bulk.get('errors')}")

    def delete_document(self, document_id: str) -> DeleteResult:
        chunks = elasticsearch_client.delete_chunks_by_document_id(document_id)
        full = full_document_client.delete_document(document_id)
        if not chunks.get("success") and not full.get("success"):
            raise SearchStoreError(chunks.get("error") or full.get("error") or "unknown error")
        return DeleteResult(deleted_chunks=chunks.get("deleted", 0) if chunks.get("success") else 0,
                            deleted_document=bool(full.get("success")))

    def clear_all(self) -> ClearResult:
        chunks = elasticsearch_client.delete_all_chunks()
        if not chunks.get("success"):
            raise SearchStoreError(f"청크 전체 삭제 실패: {chunks.get('error', 'Unknown error')}")
        documents = full_document_client.delete_all_documents()
        if not documents.get("success"):
            raise SearchStoreError(f"전체 문서 삭제 실패: {documents.get('error', 'Unknown error')}")
        return ClearResult(deleted_chunks=chunks.get("deleted", 0), deleted_documents=documents.get("deleted", 0),
                           chunks_index=chunks.get("index_name", "unknown"), documents_index=documents.get("index_name", "unknown"))

    def stats(self) -> Dict[str, Any]:
        return elasticsearch_client.get_index_stats()
