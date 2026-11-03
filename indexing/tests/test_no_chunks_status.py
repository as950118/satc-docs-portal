"""청크가 0개인 문서도 전체 문서를 색인하고 상태를 COMPLETE 로 바꾸는지 확인한다 (외부 서비스는 모두 mock).

실행: python -m unittest tests.test_no_chunks_status
"""
import sys
import unittest
from unittest.mock import MagicMock, patch

# 이 모듈들은 import 하는 순간 MinIO·ES 에 연결하므로 document_processor 를 불러오는 동안만 가짜로 바꾼다.
# patch.dict 가 끝나면 sys.modules 를 되돌려서 같은 프로세스의 다른 테스트를 오염시키지 않는다.
_STUBS = {f"app.services.{n}": MagicMock() for n in ("minio_client", "elasticsearch_client", "full_document_client", "chunking_service")}
with patch.dict(sys.modules, _STUBS):
    from app.services import document_processor as dp  # noqa: E402


def _task():
    task = MagicMock()
    task.task_id = "t1"
    task.data = {"bucket_name": "cmp-docs", "object_name": "documents/abc-123/short.md", "metadata": {}}
    return task


class NoChunksTest(unittest.TestCase):
    def _run(self, chunks, full_doc_success=True):
        with patch.object(dp, "get_queue_service", return_value=MagicMock()), \
             patch.object(dp, "minio_client") as minio, \
             patch.object(dp, "chunking_service") as chunker, \
             patch.object(dp, "elasticsearch_client") as es, \
             patch.object(dp, "full_document_client") as full, \
             patch.object(dp, "update_document_status", return_value=True) as status:
            minio.file_exists.return_value = True
            minio.download_file.return_value = "# 짧은 문서\n내용".encode("utf-8")
            minio.get_file_info.return_value = None
            chunker.chunk_document.return_value = chunks
            chunker.get_chunking_stats.return_value = {}
            es.index_chunks_bulk.return_value = {"success": len(chunks)}
            es.delete_chunks_by_source.return_value = {"deleted": 0}
            full.index_document.return_value = {"success": full_doc_success}
            result = dp.process_document_handler(_task())
            return result, full, es, status

    def test_청크가_없어도_전체_문서를_색인하고_COMPLETE로_바꾼다(self):
        result, full, es, status = self._run([])
        full.index_document.assert_called_once()
        es.index_chunks_bulk.assert_not_called()
        status.assert_called_once_with("documents/abc-123/short.md", "COMPLETE")
        self.assertEqual(result["chunks_created"], 0)
        self.assertTrue(result["full_document_indexed"])
        self.assertTrue(result["status_updated"])

    def test_전체_문서_색인이_실패하면_상태를_바꾸지_않는다(self):
        result, _, _, status = self._run([], full_doc_success=False)
        status.assert_not_called()
        self.assertFalse(result["status_updated"])

    def test_청크가_있으면_기존대로_COMPLETE로_바꾼다(self):
        result, _, es, status = self._run([{"x": 1}])
        es.index_chunks_bulk.assert_called_once()
        status.assert_called_once_with("documents/abc-123/short.md", "COMPLETE")


class FailureStatusTest(unittest.TestCase):
    """ES 색인 실패와 처리 중 예외에서 문서가 INDEXING 에 갇히지 않고 FAILED 가 되는지 확인한다."""

    def _patches(self):
        return (patch.object(dp, "get_queue_service", return_value=MagicMock()),
                patch.object(dp, "minio_client"),
                patch.object(dp, "chunking_service"),
                patch.object(dp, "elasticsearch_client"),
                patch.object(dp, "full_document_client"),
                patch.object(dp, "update_document_status", return_value=True))

    def test_ES_청크_색인이_모두_실패하면_FAILED(self):
        q, m, c, e, f, st = self._patches()
        with q, m as minio, c as chunker, e as es, f as full, st as status:
            minio.file_exists.return_value = True
            minio.download_file.return_value = "# 문서\n내용".encode("utf-8")
            minio.get_file_info.return_value = None
            chunker.chunk_document.return_value = [{"x": 1}]
            chunker.get_chunking_stats.return_value = {}
            es.delete_chunks_by_source.return_value = {"deleted": 0}
            es.index_chunks_bulk.return_value = {"success": 0, "failed": 1}
            full.index_document.return_value = {"success": True}
            dp.process_document_handler(_task())
            status.assert_called_once_with("documents/abc-123/short.md", "FAILED")

    def test_처리_중_예외가_나면_FAILED를_알리고_예외를_다시_던진다(self):
        q, m, c, e, f, st = self._patches()
        with q, m as minio, c, e, f, st as status:
            minio.file_exists.return_value = False  # "File not found" 예외 경로
            with self.assertRaises(Exception):
                dp.process_document_handler(_task())
            status.assert_called_once_with("documents/abc-123/short.md", "FAILED")


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
