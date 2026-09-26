"""
Document processing service using internal queue
"""
import logging
import requests
from typing import Dict, Any, Optional
from app.services.queue_service import get_queue_service, Task
from app.services.minio_client import minio_client
from app.services.chunking_service import chunking_service
from app.services.elasticsearch_client import elasticsearch_client
from app.services.full_document_client import full_document_client
from app.models.schemas import FullDocument
from app.config import settings

logger = logging.getLogger(__name__)


def extract_title_from_object_name(object_name: str) -> str:
    """
    MinIO objectName에서 제목을 추출
    objectName 형식: documents/UUID/title
    
    Args:
        object_name: MinIO 객체 이름
        
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
    MinIO objectName에서 UUID(document_id)를 추출
    objectName 형식: documents/UUID/title
    
    Args:
        object_name: MinIO 객체 이름
        
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
        object_name: MinIO 객체 이름 (예: documents/uuid_title.md)
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
        if settings.backend_internal_token:
            headers["X-Docs-Internal-Token"] = settings.backend_internal_token
        else:
            logger.warning("BACKEND_INTERNAL_TOKEN 미설정 — 백엔드 인증이 켜져 있으면 상태 콜백이 401로 실패합니다")

        response = requests.post(
            api_url, 
            json=payload, 
            headers=headers,
            timeout=10  # 10초 타임아웃
        )
        
        if response.status_code == 200:
            logger.info(f"Successfully updated document status: {document_id} -> {status}")
            return True
        else:
            logger.error(f"Failed to update document status: {document_id}, status_code: {response.status_code}, response: {response.text}")
            return False
            
    except Exception as e:
        logger.error(f"Error calling status update API for {object_name}: {e}")
        return False


def process_document_handler(task: Task) -> Dict[str, Any]:
    """
    Process document task handler
    
    Args:
        task: Task object containing processing data
        
    Returns:
        Processing result dictionary
    """
    task_id = task.task_id
    data = task.data
    bucket_name = data.get("bucket_name")
    object_name = data.get("object_name")
    metadata = data.get("metadata", {})
    
    logger.info(f"Starting document processing task {task_id} for {bucket_name}/{object_name}")
    
    try:
        # Update progress: Starting
        queue_service = get_queue_service()
        queue_service.update_task_progress(task_id, 10)
        
        # Step 1: Check if file exists in MinIO
        if not minio_client.file_exists(bucket_name, object_name):
            error_msg = f"File not found in MinIO: {bucket_name}/{object_name}"
            logger.error(error_msg)
            raise Exception(error_msg)
        
        # Update progress: Downloading
        queue_service.update_task_progress(task_id, 30)
        
        # Step 2: Download file from MinIO
        file_content = minio_client.download_file(bucket_name, object_name)
        if file_content is None:
            error_msg = f"Failed to download file from MinIO: {bucket_name}/{object_name}"
            logger.error(error_msg)
            raise Exception(error_msg)
        
        # Get file info
        file_info = minio_client.get_file_info(bucket_name, object_name)
        logger.info(f"Downloaded file {bucket_name}/{object_name}, size: {len(file_content)} bytes")
        
        # Update progress: Converting
        queue_service.update_task_progress(task_id, 50)
        
        # Step 3: Convert bytes to string (assuming UTF-8 encoding)
        try:
            content_str = file_content.decode("utf-8")
        except UnicodeDecodeError:
            try:
                # Try other encodings
                content_str = file_content.decode("latin-1")
            except UnicodeDecodeError as e:
                error_msg = f"Failed to decode file content: {e}"
                logger.error(error_msg)
                raise Exception(error_msg)
        
        # Update progress: Chunking
        queue_service.update_task_progress(task_id, 70)
        
        # Step 4: Extract title and document_id from object name
        title = extract_title_from_object_name(object_name)
        document_id = extract_document_id_from_object_name(object_name)
        
        # Chunk the document
        enhanced_metadata = metadata.copy() if metadata else {}
        enhanced_metadata.update({
            "bucket_name": bucket_name,
            "object_name": object_name,
            "document_id": document_id,  # UUID를 메타데이터에 추가
            "document_title": title  # 제목을 메타데이터에 추가
        })
        
        if file_info:
            enhanced_metadata.update({
                "file_size": file_info.get("size"),
                "last_modified": file_info.get("last_modified").isoformat() if file_info.get("last_modified") else None,
                "content_type": file_info.get("content_type"),
            })
        
        # Use object_name as the source file path for chunking
        chunks = chunking_service.chunk_document(content_str, object_name, enhanced_metadata)
        
        if not chunks:
            logger.warning(f"No chunks created for file: {bucket_name}/{object_name}")
            return {
                "success": True,
                "bucket_name": bucket_name,
                "object_name": object_name,
                "file_size": len(file_content),
                "chunks_created": 0,
                "elasticsearch_indexed": False,
                "message": f"No chunks created for file: {bucket_name}/{object_name}",
                "task_id": task_id
            }
        
        logger.info(f"Created {len(chunks)} chunks for file: {bucket_name}/{object_name}")
        
        # Update progress: Indexing
        queue_service.update_task_progress(task_id, 90)
        
        # Step 5: Index chunks to Elasticsearch
        # First, delete any existing chunks from the same source file
        delete_result = elasticsearch_client.delete_chunks_by_source(object_name)
        if delete_result.get("deleted", 0) > 0:
            logger.info(f"Deleted {delete_result['deleted']} existing chunks for file: {bucket_name}/{object_name}")
        
        # Index new chunks
        index_result = elasticsearch_client.index_chunks_bulk(chunks)
        
        # Get chunking statistics
        chunking_stats = chunking_service.get_chunking_stats(chunks)
        
        # Step 6: Index full document
        logger.info(f"Indexing full document for {bucket_name}/{object_name}")
        
        # Create full document object (document_id and title already extracted above)
        full_doc = FullDocument(
            document_id=document_id,
            title=title,
            content=content_str,
            metadata=enhanced_metadata
        )
        
        # Index full document
        full_doc_result = full_document_client.index_document(full_doc)
        
        # ES 인덱싱이 성공했을 때 상태 업데이트 API 호출
        status_updated = False
        if index_result["success"] > 0:
            logger.info(f"ES indexing successful for {bucket_name}/{object_name}, calling status update API")
            status_updated = update_document_status(object_name, "COMPLETE")
        
        result = {
            "success": True,
            "task_id": task_id,
            "bucket_name": bucket_name,
            "object_name": object_name,
            "file_size": len(file_content),
            "chunks_created": len(chunks),
            "elasticsearch_indexed": index_result["success"] > 0,
            "elasticsearch_results": index_result,
            "full_document_indexed": full_doc_result["success"],
            "full_document_results": full_doc_result,
            "document_title": title,
            "status_updated": status_updated,
            "chunking_stats": chunking_stats,
            "message": f"Successfully processed document: {bucket_name}/{object_name}"
        }
        
        logger.info(f"Completed document processing task {task_id}: {result}")
        return result
        
    except Exception as e:
        error_msg = f"Error processing document {bucket_name}/{object_name}: {str(e)}"
        logger.error(error_msg, exc_info=True)
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
        # Test MinIO connection (using default bucket)
        minio_healthy = True
        try:
            # Try to check if default bucket exists
            minio_client._ensure_bucket_exists()
        except Exception:
            minio_healthy = False
        
        # Test Elasticsearch connection
        es_stats = elasticsearch_client.get_index_stats()
        es_healthy = "error" not in es_stats
        
        return {
            "success": True,
            "minio_healthy": minio_healthy,
            "elasticsearch_healthy": es_healthy,
            "queue_healthy": True,
            "message": "Health check completed"
        }
        
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        return {
            "success": False,
            "error": str(e),
            "message": "Health check failed"
        }


# Register task handlers will be done after queue service is initialized
