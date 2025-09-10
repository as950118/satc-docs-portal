"""
Full document Elasticsearch client with Korean and code-optimized analyzers
"""
import logging
from typing import Dict, Any, List, Optional
from datetime import datetime
from elasticsearch import Elasticsearch
from elasticsearch.exceptions import ConnectionError as ESConnectionError, NotFoundError
from app.config import settings
from app.models.schemas import FullDocument

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)  # 명시적으로 로그 레벨 설정


class FullDocumentClient:
    """Elasticsearch client for full document indexing with Korean and code support"""
    
    def __init__(self):
        """Initialize Elasticsearch client for full documents"""
        # Construct Elasticsearch URL
        es_url = f"http://{settings.elasticsearch_host}:{settings.elasticsearch_port}"
        
        self.client = Elasticsearch(
            hosts=[es_url],
            timeout=30,
            max_retries=3,
            retry_on_timeout=True
        )
        
        self.index_name = "full_documents"
        self._index_created = False
        
        logger.info(f"Initialized FullDocumentClient with index: {self.index_name}")
    
    def _get_index_mapping(self) -> Dict[str, Any]:
        """
        Get index mapping with Korean and code-optimized analyzers
        
        Returns:
            Index mapping configuration
        """
        return {
            "settings": {
                "analysis": {
                    "analyzer": {
                        "korean_analyzer": {
                            "type": "custom",
                            "tokenizer": "standard",
                            "filter": [
                                "lowercase",
                                "cjk_width",
                                "stop"
                            ]
                        },
                        "code_analyzer": {
                            "type": "custom", 
                            "tokenizer": "keyword",
                            "filter": [
                                "lowercase",
                                "word_delimiter_graph",
                                "flatten_graph"
                            ]
                        },
                        "mixed_content_analyzer": {
                            "type": "custom",
                            "tokenizer": "standard",
                            "filter": [
                                "lowercase",
                                "word_delimiter_graph",
                                "flatten_graph",
                                "cjk_width",
                                "stop"
                            ]
                        }
                    }
                },
                "number_of_shards": 1,
                "number_of_replicas": 0
            },
            "mappings": {
                "properties": {
                    "document_id": {
                        "type": "keyword"
                    },
                    "title": {
                        "type": "text",
                        "analyzer": "korean_analyzer",
                        "fields": {
                            "keyword": {
                                "type": "keyword"
                            },
                            "mixed": {
                                "type": "text",
                                "analyzer": "mixed_content_analyzer"
                            }
                        }
                    },
                    "content": {
                        "type": "text",
                        "analyzer": "korean_analyzer",
                        "fields": {
                            "code": {
                                "type": "text",
                                "analyzer": "code_analyzer"
                            },
                            "mixed": {
                                "type": "text", 
                                "analyzer": "mixed_content_analyzer"
                            },
                            "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                            }
                        }
                    },
                    "metadata": {
                        "type": "object",
                        "properties": {
                            "bucket_name": {"type": "keyword"},
                            "object_name": {"type": "keyword"},
                            "file_size": {"type": "long"},
                            "content_type": {"type": "keyword"},
                            "last_modified": {"type": "date"}
                        }
                    },
                    "created_at": {
                        "type": "date"
                    },
                    "updated_at": {
                        "type": "date"
                    }
                }
            }
        }
    
    def _ensure_index_exists(self):
        """Ensure the full documents index exists with proper mapping"""
        if self._index_created:
            return
            
        try:
            if not self.client.indices.exists(index=self.index_name):
                mapping = self._get_index_mapping()
                
                self.client.indices.create(
                    index=self.index_name,
                    body=mapping
                )
                logger.info(f"Created full documents index: {self.index_name}")
            else:
                logger.info(f"Full documents index already exists: {self.index_name}")
            
            self._index_created = True
            
        except Exception as e:
            logger.error(f"Error ensuring full documents index exists: {e}")
            raise
    
    def index_document(self, document: FullDocument) -> Dict[str, Any]:
        """
        Index a full document to Elasticsearch
        
        Args:
            document: FullDocument object to index
            
        Returns:
            Indexing result dictionary
        """
        try:
            self._ensure_index_exists()
            
            current_time = datetime.utcnow().isoformat()
            
            doc_data = {
                "document_id": document.document_id,
                "title": document.title,
                "content": document.content,
                "metadata": document.metadata,
                "created_at": current_time,
                "updated_at": current_time
            }
            
            response = self.client.index(
                index=self.index_name,
                id=document.document_id,
                body=doc_data
            )
            
            logger.info(f"Successfully indexed full document: {document.document_id}")
            
            return {
                "success": True,
                "document_id": document.document_id,
                "result": response.get("result", "unknown")
            }
            
        except Exception as e:
            logger.error(f"Error indexing full document {document.document_id}: {e}")
            return {
                "success": False,
                "document_id": document.document_id,
                "error": str(e)
            }
    
    def search_documents(self, query: str, size: int = 10, from_: int = 0) -> Dict[str, Any]:
        """
        Search full documents
        
        Args:
            query: Search query string
            size: Number of results to return
            from_: Starting offset
            
        Returns:
            Search results dictionary
        """
        try:
            self._ensure_index_exists()
            
            search_body = {
                "query": {
                    "multi_match": {
                        "query": query,
                        "fields": [
                            "title^3",
                            "title.mixed^2", 
                            "content^2",
                            "content.mixed",
                            "content.code"
                        ],
                        "type": "best_fields",
                        "fuzziness": "AUTO"
                    }
                },
                "highlight": {
                    "fields": {
                        "title": {},
                        "content": {
                            "fragment_size": 150,
                            "number_of_fragments": 3
                        }
                    }
                },
                "size": size,
                "from": from_
            }
            
            response = self.client.search(
                index=self.index_name,
                body=search_body
            )
            
            hits = response.get("hits", {})
            total = hits.get("total", {})
            
            results = []
            for hit in hits.get("hits", []):
                result = {
                    "document_id": hit["_source"]["document_id"],
                    "title": hit["_source"]["title"],
                    "score": hit["_score"],
                    "metadata": hit["_source"]["metadata"],
                    "highlight": hit.get("highlight", {})
                }
                results.append(result)
            
            return {
                "total_results": total.get("value", 0) if isinstance(total, dict) else total,
                "returned_results": len(results),
                "max_score": hits.get("max_score", 0),
                "results": results
            }
            
        except Exception as e:
            logger.error(f"Error searching full documents: {e}")
            return {
                "total_results": 0,
                "returned_results": 0,
                "max_score": 0,
                "results": [],
                "error": str(e)
            }
    
    def get_document(self, document_id: str) -> Optional[Dict[str, Any]]:
        """
        Get a full document by ID
        
        Args:
            document_id: Document ID to retrieve
            
        Returns:
            Document data or None if not found
        """
        try:
            self._ensure_index_exists()
            
            response = self.client.get(
                index=self.index_name,
                id=document_id
            )
            
            return response.get("_source")
            
        except NotFoundError:
            logger.warning(f"Full document not found: {document_id}")
            return None
        except Exception as e:
            logger.error(f"Error getting full document {document_id}: {e}")
            return None
    
    def delete_document(self, document_id: str) -> Dict[str, Any]:
        """
        Delete a full document by ID
        
        Args:
            document_id: Document ID to delete
            
        Returns:
            Deletion result dictionary
        """
        try:
            print(f"[DEBUG] FullDocumentClient.delete_document called with: {document_id}")
            logger.info(f"Attempting to delete full document: {document_id}")
            self._ensure_index_exists()
            
            response = self.client.delete(
                index=self.index_name,
                id=document_id
            )
            
            logger.info(f"Successfully deleted full document: {document_id}")
            
            return {
                "success": True,
                "document_id": document_id,
                "result": response.get("result", "unknown")
            }
            
        except NotFoundError:
            logger.warning(f"Full document not found for deletion: {document_id}")
            return {
                "success": True,
                "document_id": document_id,
                "result": "not_found"
            }
        except Exception as e:
            logger.error(f"Error deleting full document {document_id}: {e}")
            return {
                "success": False,
                "document_id": document_id,
                "error": str(e)
            }
    
    def get_index_stats(self) -> Dict[str, Any]:
        """
        Get full documents index statistics
        
        Returns:
            Index statistics dictionary
        """
        try:
            stats = self.client.indices.stats(index=self.index_name)
            
            index_stats = stats.get("indices", {}).get(self.index_name, {})
            total_stats = index_stats.get("total", {})
            
            return {
                "document_count": total_stats.get("docs", {}).get("count", 0),
                "store_size": total_stats.get("store", {}).get("size_in_bytes", 0),
                "index_name": self.index_name
            }
            
        except Exception as e:
            logger.error(f"Error getting full documents index stats: {e}")
            return {"error": str(e)}
    
    def delete_all_documents(self) -> Dict[str, Any]:
        """
        Delete all full documents from the index
        
        Returns:
            Deletion result dictionary
        """
        try:
            print(f"[DEBUG] FullDocumentClient.delete_all_documents called")
            logger.info("Attempting to delete all full documents from index")
            
            self._ensure_index_exists()
            
            # Delete all documents in the index
            response = self.client.delete_by_query(
                index=self.index_name,
                body={
                    "query": {
                        "match_all": {}
                    }
                }
            )
            
            deleted = response.get("deleted", 0)
            logger.info(f"Successfully deleted all {deleted} full documents from index")
            
            return {
                "deleted": deleted,
                "success": True,
                "index_name": self.index_name
            }
            
        except Exception as e:
            logger.error(f"Error deleting all full documents: {e}")
            return {"deleted": 0, "success": False, "error": str(e)}


# Global full document client instance
full_document_client = FullDocumentClient()
