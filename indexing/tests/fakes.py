"""In-memory search store for tests of the write fan-out and the processor."""
from app.search.base import ClearResult, DeleteResult, SearchStoreError


class MemoryStore:
    def __init__(self, name, fail_writes=False):
        self.name = name
        self.fail_writes = fail_writes
        self.documents = {}
        self.chunks = {}
        self.marks = {}

    def _check(self):
        if self.fail_writes:
            raise SearchStoreError(f"{self.name} is down")

    def index_document(self, document, chunks):
        self._check()
        self.documents[document.document_id] = document
        self.chunks[document.document_id] = list(chunks)

    def delete_document(self, document_id):
        self._check()
        existed = self.documents.pop(document_id, None) is not None
        return DeleteResult(deleted_chunks=len(self.chunks.pop(document_id, [])), deleted_document=existed)

    def clear_all(self):
        self._check()
        result = ClearResult(sum(map(len, self.chunks.values())), len(self.documents), f"{self.name}_chunks", f"{self.name}_documents")
        self.documents.clear()
        self.chunks.clear()
        return result

    def set_reindex_mark(self, document_id, target, reason):
        if reason is None:
            self.marks.pop((document_id, target), None)
        else:
            self.marks[(document_id, target)] = reason
