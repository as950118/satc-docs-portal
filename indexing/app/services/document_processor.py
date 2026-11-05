"""
Document processing service using internal queue
"""
import logging
import requests
from typing import Any, Callable, Dict, List, Optional, Tuple
from app.services.queue_service import get_queue_service, Task
from app.services.object_storage import ObjectNotFound, object_storage
from app.services.chunking_service import chunking_service
from app.services.elasticsearch_client import elasticsearch_client
from app.models.schemas import DocumentChunk, FullDocument
from app.search.stores import get_search_stores
from app.config import settings, storage_config

logger = logging.getLogger(__name__)

# backend DocsAccessInterceptor.INTERNAL_TOKEN_HEADER 와 같아야 한다
INTERNAL_TOKEN_HEADER = "X-Internal-Token"


def extract_title_from_object_name(object_name: str) -> str:
    """
    objectName에서 제목을 추출 (색인 요청에 title 이 없을 때만 쓴다)
    objectName 형식: documents/UUID/title
    
    Args:
        object_name: 저장소 객체 키
        
    Returns:
        추출된 제목
    """
    try:
        # object_name을 '/'로 분리
        parts = object_name.split("/")
        
        # documents/UUID/title 형식 확인
        if len(parts) >= 3 and parts[0] == "documents":
            uuid_part = parts[1]
            title_part = "/".join(parts[2:])  # 여러 단계 경로도 지원
            
            # UUID 검증 (간단한 형식 체크)
            if (len(uuid_part) == 36 and 
                uuid_part.count("-") == 4 and 
                len(uuid_part.replace("-", "")) == 32):
                
                # 확장자 제거
                if "." in title_part:
                    title_part = title_part.rsplit(".", 1)[0]
                
                if title_part:
                    # URL 인코딩된 문자들을 디코딩 및 정리
                    title = title_part.replace("%20", " ").replace("_", " ")
                    return title
        
        # documents/UUID/title 패턴이 아닌 경우 파일명을 제목으로 사용
        if "/" in object_name:
            filename = object_name.split("/")[-1]
        else:
            filename = object_name
        
        if "." in filename:
            filename = filename.rsplit(".", 1)[0]
        
        return filename.replace("_", " ")
        
    except Exception as e:
        logger.warning(f"Error extracting title from object_name {object_name}: {e}")
        # 에러 발생시 파일명을 그대로 사용
        if "/" in object_name:
            filename = object_name.split("/")[-1]
        else:
            filename = object_name
        
        if "." in filename:
            filename = filename.rsplit(".", 1)[0]
        
        return filename.replace("_", " ")


def extract_document_id_from_object_name(object_name: str) -> str:
    """
    objectName에서 UUID(document_id)를 추출
    objectName 형식: documents/UUID/title
    
    Args:
        object_name: 저장소 객체 키
        
    Returns:
        추출된 UUID (document_id)
    """
    try:
        # object_name을 '/'로 분리
        parts = object_name.split("/")
        
        # documents/UUID/title 형식 확인
        if len(parts) >= 3 and parts[0] == "documents":
            uuid_part = parts[1]
            
            # UUID 검증 (간단한 형식 체크)
            if (len(uuid_part) == 36 and 
                uuid_part.count("-") == 4 and 
                len(uuid_part.replace("-", "")) == 32):
                return uuid_part
        
        # documents/UUID/title 패턴이 아닌 경우 fallback
        if "/" in object_name:
            filename = object_name.split("/")[-1]
        else:
            filename = object_name
        
        if "." in filename:
            filename = filename.rsplit(".", 1)[0]
        
        return filename
        
    except Exception as e:
        logger.warning(f"Error extracting document_id from object_name {object_name}: {e}")
        # 에러 발생시 파일명을 그대로 사용
        if "/" in object_name:
            filename = object_name.split("/")[-1]
        else:
            filename = object_name
        
        if "." in filename:
            filename = filename.rsplit(".", 1)[0]
        
        return filename


def update_document_status(object_name: str, status: str = "COMPLETE") -> bool:
    """
    문서 상태를 업데이트하는 API 호출
    
    Args:
        object_name: 저장소 객체 키 (예: documents/uuid/title.md)
        status: 업데이트할 상태 (기본값: COMPLETE)
        
    Returns:
        API 호출 성공 여부
    """
    try:
        # object_name에서 documentId(UUID) 추출
        document_id = extract_document_id_from_object_name(object_name)
        
        # API 호출 (도커 환경 고려)
        api_url = f"http://{settings.backend_api_host}:{settings.backend_api_port}/api/v1/docs/status"
        payload = {
            "documentId": document_id,
            "status": status
        }
        
        logger.info(f"Calling status update API for document {document_id} with status {status}")
        
        headers = {"Content-Type": "application/json"}
        if settings.docs_internal_token:
            headers[INTERNAL_TOKEN_HEADER] = settings.docs_internal_token
        response = requests.post(
            api_url, 
            json=payload, 
            headers=headers,
            timeout=10  # 10초 타임아웃
        )
        
        # 백엔드는 실패해도 HTTP 200 에 본문 status 로 결과를 알리는 경우가 있어 본문도 확인한다
        body_status = None
        try:
            body_status = response.json().get("status")
        except Exception:
            pass
        if response.status_code == 200 and (body_status is None or 200 <= int(body_status) < 300):
            logger.info(f"Successfully updated document status: {document_id} -> {status}")
            return True
        elif response.status_code == 200:
            logger.error(f"Status update rejected by backend: {document_id} -> {status}, body status: {body_status}, response: {response.text[:300]}")
            return False
        else:
            logger.error(f"Failed to update document status: {document_id}, status_code: {response.status_code}, response: {response.text}")
            return False
            
    except Exception as e:
        logger.error(f"Error calling status update API for {object_name}: {e}")
        return False


def build_document(bucket_name: str, object_name: str, metadata: Optional[Dict[str, Any]],
                   progress: Callable[[int], None] = lambda _: None,
                   requested_title: Optional[str] = None) -> Tuple[FullDocument, List[DocumentChunk], int]:
    """Download one object from storage and turn it into the full document and its chunks."""
    try:
        stored = object_storage.get(bucket_name, object_name)
    except ObjectNotFound:
        raise Exception(f"File not found in storage: {bucket_name}/{object_name}")
    file_content = stored.data
    progress(30)
    logger.info(f"Downloaded file {bucket_name}/{object_name}, size: {len(file_content)} bytes")
    progress(50)

    try:
        content_str = file_content.decode("utf-8")
    except UnicodeDecodeError:
        try:
            content_str = file_content.decode("latin-1")
        except UnicodeDecodeError as e:
            raise Exception(f"Failed to decode file content: {e}")
    progress(70)

    # 백엔드가 보낸 제목을 쓰고, 없으면(예전 백엔드) 키에서 뽑는다. ASCII 키에는 제목이 없다
    title = requested_title or extract_title_from_object_name(object_name)
    document_id = extract_document_id_from_object_name(object_name)
    enhanced_metadata = metadata.copy() if metadata else {}
    enhanced_metadata.update({
        "bucket_name": bucket_name,
        "object_name": object_name,
        "document_id": document_id,
        "document_title": title,
        "file_size": len(file_content),
        "last_modified": stored.last_modified.isoformat() if stored.last_modified else None,
        "content_type": stored.content_type,
    })

    chunks = chunking_service.chunk_document(content_str, object_name, enhanced_metadata)
    document = FullDocument(document_id=document_id, title=title, content=content_str, metadata=enhanced_metadata)
    return document, chunks, len(file_content)


def process_document_handler(task: Task) -> Dict[str, Any]:
    """
    Build the document, write it to the search stores, then report COMPLETE or FAILED to the portal.
    The document is COMPLETE when the active store write succeeded (app/search/stores.py).
    """
    task_id = task.task_id
    data = task.data
    bucket_name = data.get("bucket_name")
    object_name = data.get("object_name")
    requested_title = data.get("title")
    metadata = data.get("metadata", {})

    logger.info(f"Starting document processing task {task_id} for {bucket_name}/{object_name}")

    try:
        queue_service = get_queue_service()
        queue_service.update_task_progress(task_id, 10)
        document, chunks, file_size = build_document(
            bucket_name, object_name, metadata, lambda p: queue_service.update_task_progress(task_id, p),
            requested_title)
        if not chunks:
            logger.warning(f"No chunks created for file: {bucket_name}/{object_name}, indexing full document only")
        queue_service.update_task_progress(task_id, 90)

        writes = get_search_stores().index_document(document, chunks)
        status_updated = update_document_status(object_name, "COMPLETE")

        result = {
            "success": True,
            "task_id": task_id,
            "bucket_name": bucket_name,
            "object_name": object_name,
            "file_size": file_size,
            "chunks_created": len(chunks),
            "search_writes": writes,
            "document_title": document.title,
            "status_updated": status_updated,
            "chunking_stats": chunking_service.get_chunking_stats(chunks),
            "message": f"Successfully processed document: {bucket_name}/{object_name}"
        }
        logger.info(f"Completed document processing task {task_id}: {result}")
        return result

    except Exception as e:
        error_msg = f"Error processing document {bucket_name}/{object_name}: {str(e)}"
        logger.error(error_msg, exc_info=True)
        # 처리 중 예외가 나도 문서가 INDEXING 에 갇히지 않도록 실패 상태를 알린다 (update_document_status 는 예외를 던지지 않는다)
        if object_name:
            update_document_status(object_name, "FAILED")
        raise Exception(error_msg)


def health_check_handler(task: Task) -> Dict[str, Any]:
    """
    Health check task handler
    
    Args:
        task: Task object
        
    Returns:
        Health check result dictionary
    """
    try:
        try:
            storage_healthy = object_storage.bucket_exists(storage_config.bucket)
        except Exception:
            storage_healthy = False
        
        # Test Elasticsearch connection
        es_stats = elasticsearch_client.get_index_stats()
        es_healthy = "error" not in es_stats
        
        result = {
            "success": True,
            "storage_healthy": storage_healthy,
            "elasticsearch_healthy": es_healthy,
            "queue_healthy": True,
            "message": "Health check completed"
        }
        postgres = get_search_stores().postgres
        if postgres is not None:
            result["postgres_healthy"] = "error" not in postgres.stats()
        return result
        
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        return {
            "success": False,
            "error": str(e),
            "message": "Health check failed"
        }


# Register task handlers will be done after queue service is initialized
