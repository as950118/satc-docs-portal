"""
Configuration settings for the indexing server
"""
import os
from typing import Literal, Optional
from pydantic import Field, field_validator
from pydantic_settings import BaseSettings

SEARCH_CHOICES = {
    "search_backend": ("es", "postgres"),
    "search_dual_write": ("true", "false"),
    "search_pg_text_index": ("pgroonga", "trgm"),
}


class Settings(BaseSettings):
    """Application settings"""
    
    # MinIO Configuration
    minio_endpoint: str = Field(default="localhost:9000", env="MINIO_ENDPOINT")
    minio_access_key: str = Field(default="minioadmin", env="MINIO_ACCESS_KEY")
    minio_secret_key: str = Field(default="minioadmin", env="MINIO_SECRET_KEY")
    minio_secure: bool = Field(default=False, env="MINIO_SECURE")
    minio_bucket_name: str = Field(default="docs", env="MINIO_BUCKET_NAME")
    
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

    # Search store switch. Each field is read from the upper-case env var of the same name.
    search_backend: Literal["es", "postgres"] = "es"
    search_dual_write: bool = False
    search_pg_text_index: Literal["pgroonga", "trgm"] = "pgroonga"
    search_pg_host: str = "localhost"
    search_pg_port: int = 5432
    search_pg_db: str = "docs"
    search_pg_user: str = "postgres"
    search_pg_password: str = "postgres"
    search_pg_trgm_threshold: float = 0.2

    @field_validator(*SEARCH_CHOICES, mode="before")
    @classmethod
    def _one_of(cls, value, info):
        normalized = str(value).strip().lower()
        if normalized == "":
            return cls.model_fields[info.field_name].default
        allowed = SEARCH_CHOICES[info.field_name]
        if normalized not in allowed:
            raise ValueError(f"{info.field_name.upper()} 허용 값: {', '.join(allowed)} (입력: {value!r})")
        return normalized

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


# Global settings instance
settings = Settings()
