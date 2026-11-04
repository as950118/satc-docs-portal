"""문서 처리기가 검색 저장소 쓰기 결과에 따라 포털 상태를 COMPLETE 또는 FAILED 로 알리는지 확인한다 (외부 서비스는 mock).

실행: python -m pytest tests/test_no_chunks_status.py
"""
import sys
import unittest
from unittest.mock import MagicMock, patch

from app.models.schemas import DocumentChunk
from app.search.stores import SearchStores
from tests.fakes import MemoryStore

# minio_client 는 import 하는 순간 MinIO 에 연결하므로 document_processor 를 불러오는 동안만 가짜로 바꾼다.
# patch.dict 가 끝나면 sys.modules 를 되돌려서 같은 프로세스의 다른 테스트를 오염시키지 않는다.
_STUBS = {f"app.services.{n}": MagicMock() for n in ("minio_client", "chunking_service")}
with patch.dict(sys.modules, _STUBS):
    from app.services import document_processor as dp  # noqa: E402

DOC_ID = "12345678-1234-1234-1234-123456789abc"
OBJECT = f"documents/{DOC_ID}/short.md"
CHUNK = DocumentChunk(chunk_id="c1", content="내용", title="short", metadata={"source_file": OBJECT})


def _task():
    task = MagicMock()
    task.task_id = "t1"
    task.data = {"bucket_name": "cmp-docs", "object_name": OBJECT, "metadata": {}}
    return task


class ProcessorStatusTest(unittest.TestCase):
    def _run(self, chunks, active, file_exists=True):
        with patch.object(dp, "get_queue_service", return_value=MagicMock()), \
             patch.object(dp, "minio_client") as minio, \
             patch.object(dp, "chunking_service") as chunker, \
             patch.object(dp, "get_search_stores", return_value=SearchStores(active=active)), \
             patch.object(dp, "update_document_status", return_value=True) as status:
            minio.file_exists.return_value = file_exists
            minio.download_file.return_value = "# 짧은 문서\n내용".encode("utf-8")
            minio.get_file_info.return_value = None
            chunker.chunk_document.return_value = chunks
            chunker.get_chunking_stats.return_value = {}
            try:
                return dp.process_document_handler(_task()), status, None
            except Exception as e:
                return None, status, e

    def test_청크가_없어도_전체_문서를_색인하고_COMPLETE로_바꾼다(self):
        store = MemoryStore("postgres")
        result, status, error = self._run([], store)
        self.assertIsNone(error)
        self.assertEqual(store.chunks[DOC_ID], [])
        self.assertEqual(store.documents[DOC_ID].content, "# 짧은 문서\n내용")
        status.assert_called_once_with(OBJECT, "COMPLETE")
        self.assertEqual(result["chunks_created"], 0)

    def test_청크가_있으면_청크와_전체_문서를_쓰고_COMPLETE로_바꾼다(self):
        store = MemoryStore("es")
        result, status, _ = self._run([CHUNK], store)
        self.assertEqual(store.chunks[DOC_ID], [CHUNK])
        status.assert_called_once_with(OBJECT, "COMPLETE")
        self.assertEqual(result["search_writes"], {"es": "ok"})

    def test_활성_저장소_쓰기가_실패하면_FAILED를_알리고_작업도_실패한다(self):
        result, status, error = self._run([CHUNK], MemoryStore("es", fail_writes=True))
        self.assertIsNotNone(error)
        status.assert_called_once_with(OBJECT, "FAILED")

    def test_처리_중_예외가_나면_FAILED를_알리고_예외를_다시_던진다(self):
        store = MemoryStore("es")
        _, status, error = self._run([CHUNK], store, file_exists=False)
        self.assertIsNotNone(error)
        self.assertEqual(store.documents, {})
        status.assert_called_once_with(OBJECT, "FAILED")


class UpdateStatusResponseTest(unittest.TestCase):
    """백엔드가 HTTP 200 에 본문 status 500 으로 거절해도 성공으로 오판하지 않는지 확인한다."""

    def _post(self, http, body):
        resp = MagicMock()
        resp.status_code = http
        resp.json.return_value = body
        resp.text = str(body)
        return patch.object(dp.requests, "post", return_value=resp)

    def test_본문_status가_500이면_실패로_본다(self):
        with self._post(200, {"status": 500, "errorMessage": "x"}):
            self.assertFalse(dp.update_document_status("documents/abc-123/a.md", "FAILED"))

    def test_본문_status가_200이면_성공(self):
        with self._post(200, {"status": 200, "data": True}):
            self.assertTrue(dp.update_document_status("documents/abc-123/a.md", "COMPLETE"))

    def test_HTTP_오류면_실패(self):
        with self._post(500, {}):
            self.assertFalse(dp.update_document_status("documents/abc-123/a.md", "COMPLETE"))


if __name__ == "__main__":
    unittest.main()
