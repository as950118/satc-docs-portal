"""
Document chunking service using Langchain text splitters
"""
import logging
import uuid
from typing import List, Dict, Any, Optional
from langchain_text_splitters import MarkdownHeaderTextSplitter, RecursiveCharacterTextSplitter
from app.config import settings
from app.models.schemas import DocumentChunk

logger = logging.getLogger(__name__)

# Constants for chunking
HEADERS_TO_SPLIT = [
    ("#", "h1"),
    ("##", "h2"), 
    ("###", "h3"),
    ("####", "h4"),
    ("#####", "h5"),
    ("######", "h6"),
]


class ChunkingService:
    """Service for chunking documents using Langchain text splitters"""
    
    def __init__(self):
        """Initialize chunking service"""
        self.chunk_size = settings.chunk_size
        self.chunk_overlap = settings.chunk_overlap
        self.min_chunk_length = settings.min_chunk_length
        
        logger.info(f"Initialized ChunkingService with chunk_size={self.chunk_size}, chunk_overlap={self.chunk_overlap}, min_chunk_length={self.min_chunk_length}")
    
    def _extract_title_from_metadata_or_object_name(self, content: str, file_path: str, metadata: Optional[Dict[str, Any]] = None) -> str:
        """
        메타데이터나 objectName에서 제목을 추출
        
        Args:
            content: 청크 내용 (사용되지 않음, 호환성 유지)
            file_path: 파일 경로 (저장소 객체 키)
            metadata: 메타데이터
            
        Returns:
            추출된 제목
        """
        # 메타데이터에서 제목 찾기 (최우선)
        if metadata and "document_title" in metadata:
            return metadata["document_title"]
        
        # 객체 키에서 제목 추출 (documents/UUID/title 형식)
        try:
            # object_name을 '/'로 분리
            parts = file_path.split("/")
            
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
            if "/" in file_path:
                filename = file_path.split("/")[-1]
            else:
                filename = file_path
            
            if "." in filename:
                filename = filename.rsplit(".", 1)[0]
            
            return filename.replace("_", " ")
            
        except Exception as e:
            logger.warning(f"Error extracting title from object_name {file_path}: {e}")
            # 에러 발생시 파일명을 그대로 사용
            if "/" in file_path:
                filename = file_path.split("/")[-1]
            else:
                filename = file_path
            
            if "." in filename:
                filename = filename.rsplit(".", 1)[0]
            
            return filename.replace("_", " ")
    
    def _split_markdown(self, text: str, source_path: str) -> List[Dict]:
        """Markdown을 헤더→길이 기준으로 2단계 청크."""
        try:
            hsplitter = MarkdownHeaderTextSplitter(headers_to_split_on=HEADERS_TO_SPLIT)
            sections = hsplitter.split_text(text)

            rsplitter = RecursiveCharacterTextSplitter(
                chunk_size=self.chunk_size,
                chunk_overlap=self.chunk_overlap,
                separators=["\n\n", "\n", ".", "!", "?", " ", ""],  # 문장 단위 분리 추가
            )

            chunks = []
            for sec in sections:
                sub_chunks = rsplitter.split_text(sec.page_content)
                for part in sub_chunks:
                    text_content = part.strip()
                    # 최소 길이 필터링
                    if len(text_content) >= self.min_chunk_length:
                        chunks.append({
                            "text": text_content,
                            "meta": {
                                "h1": sec.metadata.get("h1"),
                                "h2": sec.metadata.get("h2"),
                                "h3": sec.metadata.get("h3"),
                                "h4": sec.metadata.get("h4"),
                                "h5": sec.metadata.get("h5"),
                                "h6": sec.metadata.get("h6"),
                            }
                        })
            return chunks
        except Exception as e:
            logger.error(f"Error splitting markdown {source_path}: {e}")
            return [{"text": text, "meta": {}}]

    def _split_plaintext(self, text: str, source_path: str) -> List[Dict]:
        """txt 등 구조 없는 문서는 길이 기준으로만 청크."""
        try:
            rsplitter = RecursiveCharacterTextSplitter(
                chunk_size=self.chunk_size,
                chunk_overlap=self.chunk_overlap,
                separators=["\n\n", "\n", ".", "!", "?", " ", ""],
            )
            chunks = rsplitter.split_text(text)
            return [{"text": chunk.strip(), "meta": {}} for chunk in chunks if chunk.strip() and len(chunk.strip()) >= self.min_chunk_length]
        except Exception as e:
            logger.error(f"Error splitting plaintext {source_path}: {e}")
            return [{"text": text, "meta": {}}]
    
    def chunk_markdown_document(self, content: str, file_path: str, metadata: Optional[Dict[str, Any]] = None) -> List[DocumentChunk]:
        """
        Chunk markdown document using improved 2-stage splitting
        
        Args:
            content: Document content as string
            file_path: Original file path
            metadata: Additional metadata
            
        Returns:
            List of DocumentChunk objects
        """
        try:
            # Use the improved markdown splitting logic
            raw_chunks = self._split_markdown(content, file_path)
            logger.info(f"Created {len(raw_chunks)} raw chunks for markdown document: {file_path}")
            
            chunks = []
            for chunk_index, raw_chunk in enumerate(raw_chunks):
                if raw_chunk["text"].strip():  # Skip empty chunks
                    chunk_id = f"{file_path}_{uuid.uuid4().hex[:8]}_{chunk_index:04d}"
                    
                    # Build comprehensive metadata
                    chunk_metadata = {
                        "source_file": file_path,
                        "chunk_index": chunk_index,
                        "chunk_type": "markdown_header",
                        "content_length": len(raw_chunk["text"]),
                    }
                    
                    # Add header information from the splitting process
                    if raw_chunk["meta"]:
                        for header_level, header_value in raw_chunk["meta"].items():
                            if header_value:
                                chunk_metadata[f"Header {header_level[1:]}"] = header_value
                    
                    # Add any additional metadata
                    if metadata:
                        chunk_metadata.update(metadata)
                    
                    # 제목 추출
                    title = self._extract_title_from_metadata_or_object_name(raw_chunk["text"], file_path, metadata)
                    
                    chunk = DocumentChunk(
                        chunk_id=chunk_id,
                        content=raw_chunk["text"],
                        title=title,
                        metadata=chunk_metadata
                    )
                    
                    chunks.append(chunk)
            
            logger.info(f"Created {len(chunks)} final chunks for markdown document: {file_path}")
            return chunks
            
        except Exception as e:
            logger.error(f"Error chunking markdown document {file_path}: {e}")
            raise
    
    def chunk_text_document(self, content: str, file_path: str, metadata: Optional[Dict[str, Any]] = None) -> List[DocumentChunk]:
        """
        Chunk plain text document using improved length-based splitting
        
        Args:
            content: Document content as string
            file_path: Original file path
            metadata: Additional metadata
            
        Returns:
            List of DocumentChunk objects
        """
        try:
            # Use the improved plaintext splitting logic
            raw_chunks = self._split_plaintext(content, file_path)
            logger.info(f"Created {len(raw_chunks)} raw chunks for text document: {file_path}")
            
            chunks = []
            for chunk_index, raw_chunk in enumerate(raw_chunks):
                if raw_chunk["text"].strip():  # Skip empty chunks
                    chunk_id = f"{file_path}_{uuid.uuid4().hex[:8]}_{chunk_index:04d}"
                    
                    chunk_metadata = {
                        "source_file": file_path,
                        "chunk_index": chunk_index,
                        "chunk_type": "text",
                        "content_length": len(raw_chunk["text"]),
                    }
                    
                    # Add any additional metadata
                    if metadata:
                        chunk_metadata.update(metadata)
                    
                    # 제목 추출
                    title = self._extract_title_from_metadata_or_object_name(raw_chunk["text"], file_path, metadata)
                    
                    chunk = DocumentChunk(
                        chunk_id=chunk_id,
                        content=raw_chunk["text"],
                        title=title,
                        metadata=chunk_metadata
                    )
                    
                    chunks.append(chunk)
            
            logger.info(f"Created {len(chunks)} final chunks for text document: {file_path}")
            return chunks
            
        except Exception as e:
            logger.error(f"Error chunking text document {file_path}: {e}")
            raise
    
    def chunk_document(self, content: str, file_path: str, metadata: Optional[Dict[str, Any]] = None) -> List[DocumentChunk]:
        """
        Chunk document based on file extension
        
        Args:
            content: Document content as string
            file_path: Original file path
            metadata: Additional metadata
            
        Returns:
            List of DocumentChunk objects
        """
        try:
            # Determine document type by file extension
            file_extension = file_path.lower().split('.')[-1] if '.' in file_path else ''
            
            if file_extension in ['md', 'markdown']:
                return self.chunk_markdown_document(content, file_path, metadata)
            else:
                # Default to text chunking for other file types
                return self.chunk_text_document(content, file_path, metadata)
                
        except Exception as e:
            logger.error(f"Error chunking document {file_path}: {e}")
            raise
    
    def get_chunking_stats(self, chunks: List[DocumentChunk]) -> Dict[str, Any]:
        """
        Get statistics about the chunking results
        
        Args:
            chunks: List of document chunks
            
        Returns:
            Dictionary with chunking statistics
        """
        if not chunks:
            return {"total_chunks": 0}
        
        total_length = sum(len(chunk.content) for chunk in chunks)
        avg_length = total_length / len(chunks) if chunks else 0
        
        chunk_types = {}
        for chunk in chunks:
            chunk_type = chunk.metadata.get("chunk_type", "unknown")
            chunk_types[chunk_type] = chunk_types.get(chunk_type, 0) + 1
        
        return {
            "total_chunks": len(chunks),
            "total_content_length": total_length,
            "average_chunk_length": avg_length,
            "chunk_types": chunk_types,
            "min_chunk_length": min(len(chunk.content) for chunk in chunks),
            "max_chunk_length": max(len(chunk.content) for chunk in chunks),
        }


# Global chunking service instance
chunking_service = ChunkingService()
