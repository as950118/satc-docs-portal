"""문서 삭제 시 청크가 실제로 지워지도록 metadata.document_id 를 올바르게 조회하는지 확인한다 (ES 는 mock).

실행: python -m unittest tests.test_delete_chunks_query
"""
import importlib.util
import os
import unittest
from unittest.mock import MagicMock, patch

# 같은 프로세스의 다른 테스트가 sys.modules 에 가짜 모듈을 남겨도 영향받지 않도록 실제 파일을 직접 불러온다
_PATH = os.path.join(os.path.dirname(__file__), "..", "app", "services", "elasticsearch_client.py")
_spec = importlib.util.spec_from_file_location("es_client_under_test", _PATH)
_module = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_module)


class DeleteChunksQueryTest(unittest.TestCase):
    def _client(self):
        ElasticsearchClient = _module.ElasticsearchClient
        with patch.object(ElasticsearchClient, "__init__", lambda self: None):
            c = ElasticsearchClient()
        c.index_name = "document_chunks"
        c.client = MagicMock()
        c._ensure_index_exists = MagicMock()
        return c

    def test_keyword_매핑과_text_keyword_매핑_모두를_조회한다(self):
        c = self._client()
        c.client.delete_by_query.return_value = {"deleted": 3}
        result = c.delete_chunks_by_document_id("abc-123")

        query = c.client.delete_by_query.call_args.kwargs["body"]["query"]
        fields = {list(clause["term"].keys())[0] for clause in query["bool"]["should"]}
        self.assertEqual(fields, {"metadata.document_id", "metadata.document_id.keyword"})
        self.assertEqual(query["bool"]["minimum_should_match"], 1)
        self.assertEqual(result["deleted"], 3)
        self.assertTrue(result["success"])


if __name__ == "__main__":
    unittest.main()
