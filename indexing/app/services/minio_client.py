"""
MinIO client service for file operations
"""
import io
import logging
from typing import Optional, BinaryIO
from minio import Minio
from minio.error import S3Error
from app.config import settings

logger = logging.getLogger(__name__)


class MinIOClient:
    """MinIO client wrapper"""
    
    def __init__(self):
        """Initialize MinIO client"""
        # Clean up endpoint - remove http:// or https:// if present
        endpoint = settings.minio_endpoint
        if endpoint.startswith('http://'):
            endpoint = endpoint[7:]
        elif endpoint.startswith('https://'):
            endpoint = endpoint[8:]
            
        self.client = Minio(
            endpoint=endpoint,
            access_key=settings.minio_access_key,
            secret_key=settings.minio_secret_key,
            secure=settings.minio_secure
        )
        self.bucket_name = settings.minio_bucket_name
        self._ensure_bucket_exists()
    
    def _ensure_bucket_exists(self) -> None:
        """Ensure the bucket exists, create if it doesn't"""
        try:
            if not self.client.bucket_exists(self.bucket_name):
                self.client.make_bucket(self.bucket_name)
                logger.info(f"Created bucket: {self.bucket_name}")
            else:
                logger.info(f"Bucket already exists: {self.bucket_name}")
        except S3Error as e:
            logger.error(f"Error checking/creating bucket: {e}")
            raise
        except Exception as e:
            logger.error(f"MinIO connection error: {e}")
            raise
    
    def download_file(self, bucket_name: str, object_name: str) -> Optional[bytes]:
        """
        Download file from MinIO
        
        Args:
            bucket_name: MinIO bucket name
            object_name: Object name (file path) in the bucket
            
        Returns:
            File content as bytes or None if error
        """
        try:
            response = self.client.get_object(bucket_name, object_name)
            content = response.read()
            response.close()
            response.release_conn()
            
            logger.info(f"Successfully downloaded file: {bucket_name}/{object_name}")
            return content
            
        except S3Error as e:
            logger.error(f"Error downloading file {bucket_name}/{object_name}: {e}")
            return None
        except Exception as e:
            logger.error(f"Unexpected error downloading file {bucket_name}/{object_name}: {e}")
            return None
    
    def download_file_stream(self, bucket_name: str, object_name: str) -> Optional[BinaryIO]:
        """
        Download file from MinIO as stream
        
        Args:
            bucket_name: MinIO bucket name
            object_name: Object name (file path) in the bucket
            
        Returns:
            File stream or None if error
        """
        try:
            response = self.client.get_object(bucket_name, object_name)
            logger.info(f"Successfully opened file stream: {bucket_name}/{object_name}")
            return response
            
        except S3Error as e:
            logger.error(f"Error opening file stream {bucket_name}/{object_name}: {e}")
            return None
        except Exception as e:
            logger.error(f"Unexpected error opening file stream {bucket_name}/{object_name}: {e}")
            return None
    
    def file_exists(self, bucket_name: str, object_name: str) -> bool:
        """
        Check if file exists in MinIO bucket
        
        Args:
            bucket_name: MinIO bucket name
            object_name: Object name (file path) in the bucket
            
        Returns:
            True if file exists, False otherwise
        """
        try:
            self.client.stat_object(bucket_name, object_name)
            return True
        except S3Error:
            return False
        except Exception as e:
            logger.error(f"Error checking file existence {bucket_name}/{object_name}: {e}")
            return False
    
    def get_file_info(self, bucket_name: str, object_name: str) -> Optional[dict]:
        """
        Get file information from MinIO
        
        Args:
            bucket_name: MinIO bucket name
            object_name: Object name (file path) in the bucket
            
        Returns:
            File information dict or None if error
        """
        try:
            stat = self.client.stat_object(bucket_name, object_name)
            return {
                "size": stat.size,
                "last_modified": stat.last_modified,
                "etag": stat.etag,
                "content_type": stat.content_type,
                "metadata": stat.metadata
            }
        except S3Error as e:
            logger.error(f"Error getting file info {bucket_name}/{object_name}: {e}")
            return None
        except Exception as e:
            logger.error(f"Unexpected error getting file info {bucket_name}/{object_name}: {e}")
            return None
    
    def upload_file(self, bucket_name: str, object_name: str, data: bytes, content_type: str = "application/octet-stream") -> bool:
        """
        Upload file to MinIO
        
        Args:
            bucket_name: MinIO bucket name
            object_name: Object name (file path) where to store the file
            data: File content as bytes
            content_type: MIME type of the file
            
        Returns:
            True if successful, False otherwise
        """
        try:
            data_stream = io.BytesIO(data)
            self.client.put_object(
                bucket_name=bucket_name,
                object_name=object_name,
                data=data_stream,
                length=len(data),
                content_type=content_type
            )
            logger.info(f"Successfully uploaded file: {bucket_name}/{object_name}")
            return True
            
        except S3Error as e:
            logger.error(f"Error uploading file {bucket_name}/{object_name}: {e}")
            return False
        except Exception as e:
            logger.error(f"Unexpected error uploading file {bucket_name}/{object_name}: {e}")
            return False


# Global MinIO client instance
minio_client = MinIOClient()
