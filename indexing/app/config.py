"""
Configuration settings for the indexing server
"""
import re
from dataclasses import dataclass, field
from urllib.parse import urlsplit

from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Application settings"""

    # 문서 파일 저장소(S3 호환). 비어 있으면 기본값이나 아래 MINIO_* 값을 쓴다 (resolve_storage_config)
    storage_endpoint: str = Field(default="", env="STORAGE_ENDPOINT")
    storage_region: str = Field(default="", env="STORAGE_REGION")
    storage_bucket: str = Field(default="", env="STORAGE_BUCKET")
    storage_access_key: str = Field(default="", env="STORAGE_ACCESS_KEY")
    storage_secret_key: str = Field(default="", env="STORAGE_SECRET_KEY")
    storage_path_style: str = Field(default="", env="STORAGE_PATH_STYLE")
    storage_manage_bucket: str = Field(default="", env="STORAGE_MANAGE_BUCKET")
    storage_public_read: str = Field(default="", env="STORAGE_PUBLIC_READ")

    # 예전 MinIO 설정. STORAGE_* 가 비어 있을 때만 쓴다
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

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


@dataclass(frozen=True)
class StorageConfig:
    """백엔드 StorageConfig 와 같은 규칙으로 푼 저장소 설정"""

    endpoint: str
    region: str
    bucket: str
    access_key: str
    secret_key: str = field(repr=False)
    path_style: bool
    manage_bucket: bool
    public_read: bool


_BUCKET_NAME = re.compile(r"^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$")


def resolve_storage_config(s: Settings) -> StorageConfig:
    """STORAGE_* 를 검증해 StorageConfig 로 만든다. 잘못된 값은 ValueError 로 기동을 멈춘다."""
    endpoint = _endpoint(s.storage_endpoint.strip() or _legacy_endpoint(s))
    bucket = s.storage_bucket.strip() or s.minio_bucket_name.strip()
    if not _BUCKET_NAME.match(bucket):
        raise ValueError("STORAGE_BUCKET 은 S3 버킷 이름 규칙(소문자·숫자·점·하이픈, 3~63자)을 따라야 합니다.")
    access_key = s.storage_access_key.strip() or s.minio_access_key.strip()
    secret_key = s.storage_secret_key.strip() or s.minio_secret_key.strip()
    if not access_key:
        raise ValueError("STORAGE_ACCESS_KEY 가 비어 있습니다.")
    if not secret_key:
        raise ValueError("STORAGE_SECRET_KEY 가 비어 있습니다.")
    return StorageConfig(
        endpoint=endpoint,
        region=s.storage_region.strip() or "us-east-1",
        bucket=bucket,
        access_key=access_key,
        secret_key=secret_key,
        path_style=_flag(s.storage_path_style, "STORAGE_PATH_STYLE", True),
        manage_bucket=_flag(s.storage_manage_bucket, "STORAGE_MANAGE_BUCKET", True),
        public_read=_flag(s.storage_public_read, "STORAGE_PUBLIC_READ", True),
    )


def _legacy_endpoint(s: Settings) -> str:
    # 예전 클라이언트처럼 MINIO_ENDPOINT 의 스킴은 버리고 MINIO_SECURE 로 http/https 를 정한다
    host = re.sub(r"^https?://", "", s.minio_endpoint.strip())
    return f"{'https' if s.minio_secure else 'http'}://{host}"


def _endpoint(raw: str) -> str:
    value = raw.rstrip("/")
    try:
        parts = urlsplit(value)
        valid = parts.scheme in ("http", "https") and bool(parts.hostname) and not parts.query and not parts.fragment
    except ValueError:
        valid = False
    if not valid:
        raise ValueError("STORAGE_ENDPOINT 는 쿼리·프래그먼트 없는 http(s) 절대 주소여야 합니다 "
                         "(예: https://<ref>.storage.supabase.co/storage/v1/s3).")
    return value


def _flag(raw: str, name: str, default: bool) -> bool:
    value = raw.strip().lower()
    if not value:
        return default
    if value in ("true", "false"):
        return value == "true"
    raise ValueError(f"{name} 은 true 또는 false 여야 합니다.")


# Global settings instance
settings = Settings()
storage_config = resolve_storage_config(settings)
