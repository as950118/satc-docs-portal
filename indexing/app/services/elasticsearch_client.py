"""
Elasticsearch client service for indexing document chunks
"""
import logging
from typing import List, Dict, Any, Optional
from elasticsearch import Elasticsearch
from elasticsearch.exceptions import ConnectionError as ESConnectionError, NotFoundError
from app.config import settings
from app.models.schemas import DocumentChunk

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)  # 명시적으로 로그 레벨 설정


class ElasticsearchClient:
    """Elasticsearch client wrapper for document indexing"""
    
    def __init__(self):
        """Initialize Elasticsearch client"""
        self.client = Elasticsearch(
            hosts=[f"http://{settings.elasticsearch_host}:{settings.elasticsearch_port}"],
            # Add authentication if needed
            # http_auth=('username', 'password'),
            # use_ssl=True,
            # verify_certs=True,
        )
        self.index_name = settings.elasticsearch_index
        # Don't create index immediately - do it lazily when needed
        self._index_created = False
    
    def _get_index_mapping(self) -> Dict[str, Any]:
        """Get the index mapping configuration"""
        return {
            "mappings": {
                "properties": {
                    "chunk_id": {
                        "type": "keyword"
                    },
                    "content": {
                        "type": "text",
                        "analyzer": "standard",
                        "fields": {
                            "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                            }
                        }
                    },
                    "metadata": {
                        "properties": {
                            "source_file": {
                                "type": "keyword"
                            },
                            "chunk_index": {
                                "type": "integer"
                            },
                            "chunk_type": {
                                "type": "keyword"
                            },
                            "content_length": {
                                "type": "integer"
                            },
                            "Header 1": {
                                "type": "text"
                            },
                            "Header 2": {
                                "type": "text"
                            },
                            "Header 3": {
                                "type": "text"
                            },
                            "Header 4": {
                                "type": "text"
                            },
                            "Header 5": {
                                "type": "text"
                            },
                            "Header 6": {
                                "type": "text"
                            }
                        }
                    },
                    "created_at": {
                        "type": "date"
                    },
                    "updated_at": {
                        "type": "date"
                    }
                }
            },
            "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0,
                "analysis": {
                    "analyzer": {
                        "standard": {
                            "type": "standard"
                        }
                    }
                }
            }
        }
    
    def _ensure_index_exists(self) -> None:
        """Ensure the index exists, create if it doesn't"""
        if self._index_created:
            return
            
        try:
            if not self.client.indices.exists(index=self.index_name):
                mapping = self._get_index_mapping()
                self.client.indices.create(
                    index=self.index_name,
                    body=mapping
                )
                logger.info(f"Created Elasticsearch index: {self.index_name}")
            else:
                logger.info(f"Elasticsearch index already exists: {self.index_name}")
            self._index_created = True
        except Exception as e:
            logger.error(f"Error checking/creating index: {e}")
            raise
    
    def index_chunk(self, chunk: DocumentChunk) -> bool:
        """
        Index a single document chunk
        
        Args:
            chunk: DocumentChunk to index
            
        Returns:
            True if successful, False otherwise
        """
        try:
            self._ensure_index_exists()
            from datetime import datetime
            current_time = datetime.utcnow().isoformat()
            doc = {
                "chunk_id": chunk.chunk_id,
                "content": chunk.content,
                "title": chunk.title,
                "metadata": chunk.metadata,
                "created_at": current_time,
                "updated_at": current_time
            }
            
            response = self.client.index(
                index=self.index_name,
                id=chunk.chunk_id,
                body=doc
            )
            
            logger.debug(f"Indexed chunk {chunk.chunk_id}: {response['result']}")
            return True
            
        except Exception as e:
            logger.error(f"Error indexing chunk {chunk.chunk_id}: {e}")
            return False
        except Exception as e:
            logger.error(f"Unexpected error indexing chunk {chunk.chunk_id}: {e}")
            return False
    
    def index_chunks_bulk(self, chunks: List[DocumentChunk]) -> Dict[str, Any]:
        """
        Index multiple document chunks using bulk API
        
        Args:
            chunks: List of DocumentChunk objects to index
            
        Returns:
            Dictionary with indexing results
        """
        if not chunks:
            return {"success": 0, "failed": 0, "errors": []}
        
        try:
            self._ensure_index_exists()
            # Prepare bulk request
            actions = []
            for chunk in chunks:
                # Add index action header
                actions.append({
                    "index": {
                        "_index": self.index_name,
                        "_id": chunk.chunk_id
                    }
                })
                # Add document data
                from datetime import datetime
                current_time = datetime.utcnow().isoformat()
                actions.append({
                    "chunk_id": chunk.chunk_id,
                    "content": chunk.content,
                    "title": chunk.title,
                    "metadata": chunk.metadata,
                    "created_at": current_time,
                    "updated_at": current_time
                })
            
            # Execute bulk request
            response = self.client.bulk(
                operations=actions
            )
            
            # Process results
            success_count = 0
            failed_count = 0
            errors = []
            
            if response.get("errors"):
                for item in response.get("items", []):
                    if "index" in item:
                        if item["index"].get("status") in [200, 201]:
                            success_count += 1
                        else:
                            failed_count += 1
                            errors.append(item["index"].get("error", "Unknown error"))
            else:
                success_count = len(chunks)
            
            logger.info(f"Bulk indexing completed: {success_count} success, {failed_count} failed")
            
            return {
                "success": success_count,
                "failed": failed_count,
                "errors": errors,
                "total": len(chunks)
            }
            
        except Exception as e:
            logger.error(f"Error in bulk indexing: {e}")
            return {
                "success": 0,
                "failed": len(chunks),
                "errors": [str(e)],
                "total": len(chunks)
            }
        except Exception as e:
            logger.error(f"Unexpected error in bulk indexing: {e}")
            return {
                "success": 0,
                "failed": len(chunks),
                "errors": [str(e)],
                "total": len(chunks)
            }
    
    def search_chunks(self, query: str, size: int = 10, source_file: Optional[str] = None) -> Dict[str, Any]:
        """
        Search for document chunks
        
        Args:
            query: Search query string
            size: Number of results to return
            source_file: Optional filter by source file
            
        Returns:
            Search results dictionary
        """
        try:
            # Build search query
            search_body = {
                "query": {
                    "bool": {
                        "must": [
                            {
                                "multi_match": {
                                    "query": query,
                                    "fields": ["title^3", "content^2", "metadata.Header 1^1.5", "metadata.Header 2^1.3", "metadata.Header 3"]
                                }
                            }
                        ]
                    }
                },
                "highlight": {
                    "fields": {
                        "content": {},
                        "title": {}
                    }
                },
                "size": size
            }
            
            # Add source file filter if specified
            if source_file:
                search_body["query"]["bool"]["filter"] = [
                    {
                        "term": {
                            "metadata.source_file": source_file
                        }
                    }
                ]
            
            response = self.client.search(
                index=self.index_name,
                body=search_body
            )
            
            # Process results
            hits = response.get("hits", {})
            results = []
            
            for hit in hits.get("hits", []):
                result = {
                    "chunk_id": hit["_id"],
                    "score": hit["_score"],
                    "content": hit["_source"]["content"],
                    "title": hit["_source"].get("title", ""),
                    "metadata": hit["_source"]["metadata"],
                    "highlight": hit.get("highlight", {})
                }
                results.append(result)
            
            return {
                "total": hits.get("total", {}).get("value", 0),
                "results": results,
                "max_score": hits.get("max_score", 0)
            }
            
        except Exception as e:
            logger.error(f"Error searching chunks: {e}")
            return {"total": 0, "results": [], "max_score": 0, "error": str(e)}
        except Exception as e:
            logger.error(f"Unexpected error searching chunks: {e}")
            return {"total": 0, "results": [], "max_score": 0, "error": str(e)}
    
    def delete_chunks_by_source(self, source_file: str) -> Dict[str, Any]:
        """
        Delete all chunks from a specific source file
        
        Args:
            source_file: Source file path
            
        Returns:
            Deletion results dictionary
        """
        try:
            print(f"[DEBUG] ElasticsearchClient.delete_chunks_by_source called with: {source_file}")
            logger.info(f"Attempting to delete chunks for source file: {source_file}")
            response = self.client.delete_by_query(
                index=self.index_name,
                body={
                    "query": {
                        "term": {
                            "metadata.source_file": source_file
                        }
                    }
                }
            )
            
            deleted = response.get("deleted", 0)
            logger.info(f"Deleted {deleted} chunks from source file: {source_file}")
            
            return {
                "deleted": deleted,
                "success": True
            }
            
        except Exception as e:
            logger.error(f"Error deleting chunks from source {source_file}: {e}")
            return {"deleted": 0, "success": False, "error": str(e)}
        except Exception as e:
            logger.error(f"Unexpected error deleting chunks from source {source_file}: {e}")
            return {"deleted": 0, "success": False, "error": str(e)}
    
    def get_index_stats(self) -> Dict[str, Any]:
        """
        Get index statistics
        
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
            logger.error(f"Error getting index stats: {e}")
            return {"error": str(e)}
        except Exception as e:
            logger.error(f"Unexpected error getting index stats: {e}")
            return {"error": str(e)}
    
    def delete_chunks_by_document_id(self, document_id: str) -> Dict[str, Any]:
        """
        Delete all chunks for a specific document ID
        
        Args:
            document_id: Document ID to delete chunks for
            
        Returns:
            Deletion result dictionary
        """
        try:
            print(f"[DEBUG] ElasticsearchClient.delete_chunks_by_document_id called with: {document_id}")
            logger.info(f"Attempting to delete chunks for document ID: {document_id}")
            self._ensure_index_exists()
            
            # documentId로 object_name 패턴을 구성 (documents/{document_id}.md 형태)
            object_name_patterns = [
                f"documents/{document_id}.md",
                f"documents/{document_id}",
                document_id  # 직접 매칭도 시도
            ]
            
            total_deleted = 0
            
            for pattern in object_name_patterns:
                # Query to find chunks with matching object_name
                query = {
                    "query": {
                        "bool": {
                            "should": [
                                {
                                    "term": {
                                        "metadata.object_name.keyword": pattern
                                    }
                                },
                                {
                                    "wildcard": {
                                        "metadata.object_name.keyword": f"*/{document_id}*"
                                    }
                                }
                            ]
                        }
                    }
                }
                
                # Delete by query
                response = self.client.delete_by_query(
                    index=self.index_name,
                    body=query
                )
                
                deleted_count = response.get("deleted", 0)
                total_deleted += deleted_count
                
                if deleted_count > 0:
                    logger.info(f"Deleted {deleted_count} chunks for pattern: {pattern}")
            
            if total_deleted > 0:
                logger.info(f"Successfully deleted {total_deleted} chunks for document ID: {document_id}")
                return {"deleted": total_deleted, "success": True, "document_id": document_id}
            else:
                logger.warning(f"No chunks found for document ID: {document_id}")
                return {"deleted": 0, "success": True, "document_id": document_id, "message": "No chunks found"}
            
        except Exception as e:
            logger.error(f"Error deleting chunks for document ID {document_id}: {e}")
            return {"deleted": 0, "success": False, "error": str(e), "document_id": document_id}
    
    def delete_all_chunks(self) -> Dict[str, Any]:
        """
        Delete all chunks from the index
        
        Returns:
            Deletion result dictionary
        """
        try:
            print(f"[DEBUG] ElasticsearchClient.delete_all_chunks called")
            logger.info("Attempting to delete all chunks from index")
            
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
            logger.info(f"Successfully deleted all {deleted} chunks from index")
            
            return {
                "deleted": deleted,
                "success": True,
                "index_name": self.index_name
            }
            
        except Exception as e:
            logger.error(f"Error deleting all chunks: {e}")
            return {"deleted": 0, "success": False, "error": str(e)}


# Global Elasticsearch client instance
elasticsearch_client = ElasticsearchClient()
