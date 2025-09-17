"""
Configuration settings for the indexing server
"""
import os
from typing import Optional
from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Application settings"""
    
    # MinIO Configuration
    minio_endpoint: str = Field(default="localhost:9000", env="MINIO_ENDPOINT")
    minio_access_key: str = Field(default="minioadmin", env="MINIO_ACCESS_KEY")
    minio_secret_key: str = Field(default="minioadmin", env="MINIO_SECRET_KEY")
    minio_secure: bool = Field(default=False, env="MINIO_SECURE")
    minio_bucket_name: str = Field(default="cmp-docs", env="MINIO_BUCKET_NAME")
    
    # Elasticsearch Configuration
    elasticsearch_host: str = Field(default="localhost", env="ELASTICSEARCH_HOST")
    elasticsearch_port: int = Field(default=9200, env="ELASTICSEARCH_PORT")
    elasticsearch_index: str = Field(default="document_chunks", env="ELASTICSEARCH_INDEX")
    
    # Internal Queue Configuration
    queue_max_workers: int = Field(default=4, env="QUEUE_MAX_WORKERS")
    
    # Server Configuration
    server_host: str = Field(default="0.0.0.0", env="SERVER_HOST")
    server_port: int = Field(default=8000, env="SERVER_PORT")
    debug: bool = Field(default=True, env="DEBUG")
    
    # Chunking Configuration
    chunk_size: int = Field(default=1000, env="CHUNK_SIZE")
    chunk_overlap: int = Field(default=100, env="CHUNK_OVERLAP")
    min_chunk_length: int = Field(default=100, env="MIN_CHUNK_LENGTH")
    
    # Backend API Configuration
    backend_api_host: str = Field(default="localhost", env="BACKEND_API_HOST")
    backend_api_port: int = Field(default=8080, env="BACKEND_API_PORT")
    
    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


# Global settings instance
settings = Settings()
