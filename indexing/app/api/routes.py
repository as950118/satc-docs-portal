"""
FastAPI routes for the indexing server
"""
import logging
from typing import Dict, Any, Optional
from fastapi import APIRouter, HTTPException, Query
from app.models.schemas import (
    DocumentProcessRequest, 
    DocumentProcessResponse, 
    TaskStatusResponse, 
    TaskStatus,
    DocumentDeleteRequest,
    DocumentDeleteResponse,
    ClearAllResponse
)
from app.services.queue_service import get_queue_service, TaskStatus as QueueTaskStatus
from app.services.elasticsearch_client import elasticsearch_client
from app.services import document_processor  # Import to register handlers
from app.services.document_processor import extract_document_id_from_object_name
from app.search.stores import get_search_stores

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)  # 명시적으로 로그 레벨 설정

# Create router
router = APIRouter()


@router.post("/process-document", response_model=DocumentProcessResponse)
async def process_document(request: DocumentProcessRequest) -> DocumentProcessResponse:
    """
    Process a document: download from MinIO, chunk, and index to Elasticsearch
    
    Args:
        request: Document processing request
        
    Returns:
        Document processing response with task ID
    """
    try:
        logger.info(f"Received document processing request for file: {request.bucket_name}/{request.object_name}")
        
        # Submit task to internal queue
        queue_service = get_queue_service() 
        task_id = queue_service.submit_task(
            task_type="process_document",
            data={
                "bucket_name": request.bucket_name,
                "object_name": request.object_name,
                "metadata": request.metadata
            }
        )
        
        logger.info(f"Submitted processing task {task_id} for file: {request.bucket_name}/{request.object_name}")
        
        return DocumentProcessResponse(
            task_id=task_id,
            status=TaskStatus.PENDING,
            message=f"문서 처리 작업이 큐에 추가되었습니다. 작업 ID: {task_id}"
        )
        
    except Exception as e:
        logger.error(f"Error submitting document processing task: {e}")
        raise HTTPException(
            status_code=500,
            detail=f"문서 처리 작업 제출 중 오류가 발생했습니다: {str(e)}"
        )


@router.get("/status/{task_id}", response_model=TaskStatusResponse)
async def get_task_status(task_id: str) -> TaskStatusResponse:
    """
    Get the status of a processing task
    
    Args:
        task_id: Task ID to check status for
        
    Returns:
        Task status response
    """
    try:
        # Get task from internal queue
        queue_service = get_queue_service()
        task = queue_service.get_task_status(task_id)
        
        if not task:
            raise HTTPException(
                status_code=404,
                detail=f"작업을 찾을 수 없습니다: {task_id}"
            )
        
        # Map internal task status to API task status
        status_mapping = {
            QueueTaskStatus.PENDING: TaskStatus.PENDING,
            QueueTaskStatus.PROCESSING: TaskStatus.PROCESSING,
            QueueTaskStatus.COMPLETED: TaskStatus.COMPLETED,
            QueueTaskStatus.FAILED: TaskStatus.FAILED
        }
        
        api_status = status_mapping.get(task.status, TaskStatus.FAILED)
        
        return TaskStatusResponse(
            task_id=task_id,
            status=api_status,
            progress=task.progress,
            result=task.result,
            error_message=task.error
        )
            
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error getting task status for {task_id}: {e}")
        raise HTTPException(
            status_code=500,
            detail=f"작업 상태 조회 중 오류가 발생했습니다: {str(e)}"
        )


@router.get("/search")
async def search_documents(
    q: str = Query(..., description="검색 쿼리"),
    size: int = Query(10, ge=1, le=100, description="반환할 결과 수"),
    source_file: Optional[str] = Query(None, description="특정 소스 파일로 필터링")
) -> Dict[str, Any]:
    """
    Search indexed document chunks
    
    Args:
        q: Search query string
        size: Number of results to return
        source_file: Optional filter by source file
        
    Returns:
        Search results
    """
    try:
        logger.info(f"Received search request: query='{q}', size={size}, source_file={source_file}")
        
        return {"query": q, **get_search_stores().active.search_chunks(query=q, size=size, source_file=source_file)}

    except Exception as e:
        logger.error(f"Error in search: {e}")
        raise HTTPException(
            status_code=500,
            detail=f"검색 중 오류가 발생했습니다: {str(e)}"
        )


@router.get("/health")
async def health_check() -> Dict[str, Any]:
    """
    Health check endpoint
    
    Returns:
        Health status of the service
    """
    try:
        # Submit health check task to internal queue
        queue_service = get_queue_service()
        task_id = queue_service.submit_task("health_check", {})
        
        # Wait for result (simple polling for health check)
        import time
        max_wait = 10  # 10 seconds
        wait_time = 0.1
        total_waited = 0
        
        while total_waited < max_wait:
            task = queue_service.get_task_status(task_id)
            if task and task.status == QueueTaskStatus.COMPLETED:
                return {
                    "status": "healthy" if task.result.get("success") else "unhealthy",
                    "details": task.result,
                    "message": "서비스 상태 확인 완료"
                }
            elif task and task.status == QueueTaskStatus.FAILED:
                return {
                    "status": "unhealthy",
                    "details": {"error": task.error},
                    "message": "서비스 상태 확인 실패"
                }
            
            time.sleep(wait_time)
            total_waited += wait_time
        
        # Timeout
        return {
            "status": "unhealthy",
            "details": {"error": "Health check timeout"},
            "message": "서비스 상태 확인 시간 초과"
        }
        
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        return {
            "status": "unhealthy",
            "details": {"error": str(e)},
            "message": "서비스 상태 확인 실패"
        }


@router.get("/stats")
async def get_stats() -> Dict[str, Any]:
    """
    Get system statistics
    
    Returns:
        System statistics
    """
    try:
        # Get Elasticsearch index stats
        es_stats = elasticsearch_client.get_index_stats()
        
        # Get internal queue stats
        queue_service = get_queue_service()
        queue_stats = queue_service.get_queue_stats()
        
        stats = {
            "elasticsearch": es_stats,
            "queue": queue_stats,
            "message": "시스템 통계 조회 완료"
        }
        postgres = get_search_stores().postgres
        if postgres is not None:
            stats["postgres"] = postgres.stats()
        return stats
        
    except Exception as e:
        logger.error(f"Error getting stats: {e}")
        raise HTTPException(
            status_code=500,
            detail=f"시스템 통계 조회 중 오류가 발생했습니다: {str(e)}"
        )


@router.delete("/documents", response_model=DocumentDeleteResponse)
async def delete_documents(request: DocumentDeleteRequest) -> DocumentDeleteResponse:
    """
    파일 이름 리스트를 받아서 해당하는 모든 청크와 전체 문서를 일괄 삭제
    
    Args:
        request: 문서 삭제 요청 (files 리스트 포함)
        
    Returns:
        문서 삭제 응답
    """
    try:
        print(f"[DEBUG] DELETE request received for {len(request.files)} files")
        logger.info(f"Received document deletion request for {len(request.files)} files: {request.files}")
        
        # 결과 추적 변수들
        results = []
        total_deleted_chunks = 0
        total_deleted_documents = 0
        successful_deletions = 0
        failed_deletions = 0
        
        # 각 파일에 대해 삭제 수행
        for file_name in request.files:
            try:
                print(f"[DEBUG] Processing deletion for file: {file_name}")
                logger.info(f"Starting deletion process for file: {file_name}")
                
                # objectName에서 document_id(UUID) 추출
                object_name = f"documents/{file_name}"
                document_id = extract_document_id_from_object_name(object_name)
                deleted = get_search_stores().delete_document(document_id)
                successful_deletions += 1
                total_deleted_chunks += deleted.deleted_chunks
                if deleted.deleted_document:
                    total_deleted_documents += 1
                logger.info(f"Successfully processed {file_name}: chunks={deleted.deleted_chunks}, full_doc={deleted.deleted_document}")
                results.append({
                    "file_name": file_name,
                    "success": True,
                    "deleted_chunks": deleted.deleted_chunks,
                    "deleted_full_document": deleted.deleted_document,
                    "message": f"Successfully deleted {deleted.deleted_chunks} chunks and full document"
                })

            except Exception as file_error:
                failed_deletions += 1
                error_message = f"Error processing {file_name}: {str(file_error)}"
                logger.error(error_message)
                
                results.append({
                    "file_name": file_name,
                    "success": False,
                    "deleted_chunks": 0,
                    "deleted_full_document": False,
                    "message": error_message
                })
        
        # 전체 결과 메시지 생성
        overall_success = failed_deletions == 0
        message = f"Bulk deletion completed: {successful_deletions} successful, {failed_deletions} failed"
        
        logger.info(f"Bulk deletion summary: total_files={len(request.files)}, successful={successful_deletions}, failed={failed_deletions}, total_chunks_deleted={total_deleted_chunks}, total_docs_deleted={total_deleted_documents}")
        
        return DocumentDeleteResponse(
            success=overall_success,
            total_files=len(request.files),
            successful_deletions=successful_deletions,
            failed_deletions=failed_deletions,
            total_deleted_chunks=total_deleted_chunks,
            total_deleted_documents=total_deleted_documents,
            results=results,
            message=message
        )
        
    except Exception as e:
        logger.error(f"Error in document deletion: {e}")
        raise HTTPException(
            status_code=500,
            detail=f"문서 삭제 중 오류가 발생했습니다: {str(e)}"
        )


@router.get("/search-documents")
async def search_full_documents(
    q: str = Query(..., description="검색 쿼리"),
    size: int = Query(default=10, ge=1, le=100, description="반환할 결과 수"),
    from_: int = Query(default=0, ge=0, alias="from", description="시작 오프셋")
):
    """
    전체 문서에서 검색 (한국어 및 코드 최적화된 analyzer 사용)
    
    Args:
        q: 검색 쿼리
        size: 반환할 결과 수 (1-100)
        from_: 시작 오프셋
        
    Returns:
        검색 결과
    """
    try:
        logger.info(f"Full document search request: query='{q}', size={size}, from={from_}")
        
        results = get_search_stores().active.search_documents(query=q, size=size, from_=from_)
        logger.info(f"Full document search completed: {results['returned_results']} results returned")
        return {"query": q, **results}

    except Exception as e:
        logger.error(f"Error in full document search: {e}")
        raise HTTPException(
            status_code=500,
            detail=f"전체 문서 검색 중 오류가 발생했습니다: {str(e)}"
        )


@router.get("/documents/{document_id}")
async def get_full_document(document_id: str):
    """
    문서 ID로 전체 문서 조회
    
    Args:
        document_id: 조회할 문서 ID
        
    Returns:
        전체 문서 데이터
    """
    try:
        logger.info(f"Get full document request: document_id={document_id}")
        
        document = get_search_stores().active.get_document(document_id)
        
        if document is None:
            raise HTTPException(
                status_code=404,
                detail=f"문서를 찾을 수 없습니다: {document_id}"
            )
        
        logger.info(f"Full document retrieved: {document_id}")
        
        return document
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error getting full document: {e}")
        raise HTTPException(
            status_code=500,
            detail=f"문서 조회 중 오류가 발생했습니다: {str(e)}"
        )


@router.delete("/clear-all", response_model=ClearAllResponse)
async def clear_all_data() -> ClearAllResponse:
    """
    ES에 있는 모든 청크와 전체 문서를 삭제
    
    Returns:
        전체 삭제 응답
    """
    try:
        print(f"[DEBUG] CLEAR ALL request received")
        logger.info("Received request to clear all chunks and documents from Elasticsearch")
        
        cleared = get_search_stores().clear_all()
        deleted_chunks = cleared.deleted_chunks
        deleted_documents = cleared.deleted_documents
        chunks_index = cleared.chunks_index
        documents_index = cleared.documents_index

        # 결과 메시지 생성
        message = f"Successfully cleared all data: {deleted_chunks} chunks and {deleted_documents} documents deleted"
        
        logger.info(f"Clear all operation completed: chunks={deleted_chunks}, documents={deleted_documents}")
        
        return ClearAllResponse(
            success=True,
            deleted_chunks=deleted_chunks,
            deleted_documents=deleted_documents,
            chunks_index=chunks_index,
            documents_index=documents_index,
            message=message
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error in clear all operation: {e}")
        raise HTTPException(
            status_code=500,
            detail=f"전체 데이터 삭제 중 오류가 발생했습니다: {str(e)}"
        )
