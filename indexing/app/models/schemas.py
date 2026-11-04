"""
Pydantic models for request/response schemas
"""
from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field
from enum import Enum


class TaskStatus(str, Enum):
    """Task status enumeration"""
    PENDING = "pending"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"


class DocumentProcessRequest(BaseModel):
    """Request model for document processing"""
    bucket_name: str = Field(..., description="저장소 버킷 이름", alias="bucketName")
    object_name: str = Field(..., description="저장소 객체 키 (파일 경로)", alias="objectName")
    title: Optional[str] = Field(default=None, description="검색 색인에 쓸 문서 제목. 없으면 objectName 에서 뽑는다")
    metadata: Optional[Dict[str, Any]] = Field(default=None, description="추가 메타데이터")
    
    class Config:
        json_schema_extra = {
            "example": {
                "bucketName": "docs",
                "objectName": "documents/0f1e2d3c-4b5a-6978-8a9b-0c1d2e3f4a5b/0f1e2d3c-4b5a-6978-8a9b-0c1d2e3f4a5b.md",
                "title": "배포 가이드",
                "metadata": {
                    "source": "user_upload",
                    "category": "documentation"
                }
            }
        }



class DocumentProcessResponse(BaseModel):
    """Response model for document processing"""
    task_id: str = Field(..., description="작업 ID")
    status: TaskStatus = Field(..., description="작업 상태")
    message: str = Field(..., description="응답 메시지")
    
    class Config:
        json_schema_extra = {
            "example": {
                "task_id": "12345678-1234-1234-1234-123456789012",
                "status": "pending",
                "message": "문서 처리 작업이 큐에 추가되었습니다."
            }
        }


class TaskStatusResponse(BaseModel):
    """Response model for task status"""
    task_id: str = Field(..., description="작업 ID")
    status: TaskStatus = Field(..., description="작업 상태")
    progress: Optional[int] = Field(default=None, description="진행률 (0-100)")
    result: Optional[Dict[str, Any]] = Field(default=None, description="작업 결과")
    error_message: Optional[str] = Field(default=None, description="오류 메시지")
    
    class Config:
        json_schema_extra = {
            "example": {
                "task_id": "12345678-1234-1234-1234-123456789012",
                "status": "completed",
                "progress": 100,
                "result": {
                    "chunks_created": 25,
                    "elasticsearch_indexed": True
                },
                "error_message": None
            }
        }


class DocumentChunk(BaseModel):
    """Document chunk model"""
    chunk_id: str = Field(..., description="청크 ID")
    content: str = Field(..., description="청크 내용")
    title: str = Field(..., description="문서 제목")
    metadata: Dict[str, Any] = Field(..., description="청크 메타데이터")
    
    class Config:
        json_schema_extra = {
            "example": {
                "chunk_id": "doc_001_chunk_001",
                "content": "이것은 문서의 첫 번째 청크입니다...",
                "title": "MNRP-BE-1 : 개발 가이드 정책",
                "metadata": {
                    "source_file": "documents/sample.md",
                    "chunk_index": 0,
                    "header": "Introduction"
                }
            }
        }


class DocumentDeleteRequest(BaseModel):
    """Request model for document deletion"""
    files: List[str] = Field(..., description="삭제할 파일 경로 리스트 (UUID/title 형식, 확장자 포함)")
    
    class Config:
        json_schema_extra = {
            "example": {
                "files": [
                    "12345678-1234-1234-1234-123456789abc/API_Documentation.md",
                    "87654321-4321-4321-4321-cba987654321/User_Guide.md"
                ]
            }
        }


class DocumentDeleteResponse(BaseModel):
    """Response model for bulk document deletion"""
    success: bool = Field(..., description="전체 작업 성공 여부")
    total_files: int = Field(..., description="처리된 총 파일 수")
    successful_deletions: int = Field(..., description="성공적으로 삭제된 파일 수")
    failed_deletions: int = Field(..., description="삭제 실패한 파일 수")
    total_deleted_chunks: int = Field(..., description="삭제된 총 청크 수")
    total_deleted_documents: int = Field(..., description="삭제된 총 전체 문서 수")
    results: List[Dict[str, Any]] = Field(..., description="각 파일별 삭제 결과")
    message: str = Field(..., description="결과 메시지")
    
    class Config:
        json_schema_extra = {
            "example": {
                "success": True,
                "total_files": 3,
                "successful_deletions": 2,
                "failed_deletions": 1,
                "total_deleted_chunks": 25,
                "total_deleted_documents": 2,
                "results": [
                    {
                        "file_name": "abc-dwer-123.md",
                        "success": True,
                        "deleted_chunks": 15,
                        "deleted_full_document": True,
                        "message": "Successfully deleted"
                    },
                    {
                        "file_name": "test-document.md",
                        "success": True,
                        "deleted_chunks": 10,
                        "deleted_full_document": True,
                        "message": "Successfully deleted"
                    },
                    {
                        "file_name": "guide-policy.md",
                        "success": False,
                        "deleted_chunks": 0,
                        "deleted_full_document": False,
                        "message": "File not found"
                    }
                ],
                "message": "Bulk deletion completed: 2 successful, 1 failed"
            }
        }


class ClearAllResponse(BaseModel):
    """Response model for clearing all documents and chunks"""
    success: bool = Field(..., description="전체 작업 성공 여부")
    deleted_chunks: int = Field(..., description="삭제된 총 청크 수")
    deleted_documents: int = Field(..., description="삭제된 총 전체 문서 수")
    chunks_index: str = Field(..., description="청크 인덱스 이름")
    documents_index: str = Field(..., description="문서 인덱스 이름")
    message: str = Field(..., description="결과 메시지")
    
    class Config:
        json_schema_extra = {
            "example": {
                "success": True,
                "deleted_chunks": 150,
                "deleted_documents": 25,
                "chunks_index": "document_chunks",
                "documents_index": "full_documents",
                "message": "Successfully cleared all data: 150 chunks and 25 documents deleted"
            }
        }


class FullDocument(BaseModel):
    """Full document model for Elasticsearch indexing"""
    document_id: str = Field(..., description="문서 고유 ID")
    title: str = Field(..., description="문서 제목")
    content: str = Field(..., description="문서 전체 내용")
    metadata: Dict[str, Any] = Field(default_factory=dict, description="문서 메타데이터")
    
    class Config:
        json_schema_extra = {
            "example": {
                "document_id": "740d31ce-33f1-4713-9ae4-11bf4572f7a6",
                "title": "MNRP-BE-1 : 개발 가이드 정책",
                "content": "# MNRP-BE-1 : 개발 가이드 정책\n\n## 1. 개요\n...",
                "metadata": {
                    "bucket_name": "docs",
                    "object_name": "documents/740d31ce-33f1-4713-9ae4-11bf4572f7a6.md",
                    "file_size": 13899,
                    "content_type": "application/octet-stream"
                }
            }
        }
