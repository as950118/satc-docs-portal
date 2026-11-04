"""색인 요청의 title 이 검색 색인 제목이 되는지, 없으면 예전처럼 객체 키에서 뽑는지 확인한다 (ES·저장소는 mock).

실행: python -m pytest tests/test_document_title.py
"""
from unittest.mock import MagicMock, patch

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api import routes
from app.services import document_processor as dp
from app.search.stores import SearchStores
from app.services.object_storage import StoredObject
from tests.fakes import MemoryStore

DOC_ID = "0f1e2d3c-4b5a-6978-8a9b-0c1d2e3f4a5b"
BODY = "# 배포 가이드\n\n" + "배포 절차를 설명하는 본문입니다. " * 20


def _submitted_task_data(payload):
    app = FastAPI()
    app.include_router(routes.router, prefix="/api/v1")
    queue = MagicMock()
    queue.submit_task.return_value = "task-1"
    with patch.object(routes, "get_queue_service", return_value=queue):
        response = TestClient(app).post("/api/v1/process-document", json=payload)
    assert response.status_code == 200
    return queue.submit_task.call_args.kwargs["data"]


def test_receiver_passes_the_title_to_the_task():
    data = _submitted_task_data({"bucketName": "docs", "objectName": f"documents/{DOC_ID}/{DOC_ID}.md", "title": "배포 가이드(v2)"})

    assert data["title"] == "배포 가이드(v2)"
    assert data["object_name"] == f"documents/{DOC_ID}/{DOC_ID}.md"


def test_receiver_accepts_requests_without_a_title():
    data = _submitted_task_data({"bucketName": "docs", "objectName": f"documents/{DOC_ID}/배포_가이드.md"})

    assert data["title"] is None


def _index(object_name, title):
    task = MagicMock()
    task.task_id = "t1"
    task.data = {"bucket_name": "docs", "object_name": object_name, "title": title, "metadata": None}
    store = MemoryStore("es")
    with patch.object(dp, "get_queue_service", return_value=MagicMock()), \
         patch.object(dp, "object_storage") as storage, \
         patch.object(dp, "get_search_stores", return_value=SearchStores(active=store)), \
         patch.object(dp, "update_document_status", return_value=True):
        storage.get.return_value = StoredObject(data=BODY.encode("utf-8"), content_type="text/markdown", last_modified=None)
        dp.process_document_handler(task)
    return store.chunks[DOC_ID], store.documents[DOC_ID]


def test_given_title_is_the_index_title_even_for_an_id_only_key():
    chunks, full_doc = _index(f"documents/{DOC_ID}/{DOC_ID}.md", "배포 가이드(v2)")

    assert full_doc.title == "배포 가이드(v2)"
    assert full_doc.document_id == DOC_ID
    assert chunks and {c.title for c in chunks} == {"배포 가이드(v2)"}
    assert {c.metadata["document_title"] for c in chunks} == {"배포 가이드(v2)"}


def test_without_a_title_the_key_title_is_used_as_before():
    chunks, full_doc = _index(f"documents/{DOC_ID}/배포_가이드.md", None)

    assert full_doc.title == "배포 가이드"
    assert {c.title for c in chunks} == {"배포 가이드"}
