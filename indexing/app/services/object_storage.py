"""
S3 호환 저장소(MinIO, Supabase Storage, Cloudflare R2) 접근
"""
import json
import logging
from dataclasses import dataclass
from datetime import datetime
from typing import Optional, Protocol

import boto3
from botocore.config import Config
from botocore.exceptions import ClientError

from app.config import StorageConfig, storage_config

logger = logging.getLogger(__name__)

# botocore 의 DEBUG 로그에는 서명 헤더(액세스 키 ID)가 찍힌다. DEBUG=true 로 띄워도 남지 않게 막는다
for _name in ("botocore", "boto3", "s3transfer"):
    logging.getLogger(_name).setLevel(logging.INFO)

_NOT_FOUND = {"404", "NoSuchKey", "NoSuchBucket", "NotFound"}


class ObjectNotFound(Exception):
    pass


@dataclass(frozen=True)
class StoredObject:
    data: bytes
    content_type: Optional[str]
    last_modified: Optional[datetime]


class ObjectStorage(Protocol):
    def get(self, bucket: str, key: str) -> StoredObject: ...

    def put(self, bucket: str, key: str, data: bytes, content_type: str) -> None: ...

    def exists(self, bucket: str, key: str) -> bool: ...

    def copy(self, bucket: str, source_key: str, target_key: str) -> None: ...

    def delete(self, bucket: str, key: str) -> None: ...

    def bucket_exists(self, bucket: str) -> bool: ...

    def ensure_bucket(self) -> None: ...


def _not_found(error: ClientError) -> bool:
    return error.response.get("Error", {}).get("Code") in _NOT_FOUND


class S3ObjectStorage:
    """boto3 S3 클라이언트 구현. 엔드포인트 경로(/storage/v1/s3)를 지키려면 path-style 이어야 한다."""

    def __init__(self, config: StorageConfig):
        self.config = config
        self.client = boto3.client(
            "s3",
            endpoint_url=config.endpoint,
            region_name=config.region,
            aws_access_key_id=config.access_key,
            aws_secret_access_key=config.secret_key,
            config=Config(
                signature_version="s3v4",
                s3={"addressing_style": "path" if config.path_style else "virtual"},
                # boto3 1.36 부터 기본으로 붙는 CRC 체크섬을 S3 호환 저장소 일부가 거부해서, 필요할 때만 보낸다
                request_checksum_calculation="when_required",
                response_checksum_validation="when_required",
            ),
        )

    def get(self, bucket: str, key: str) -> StoredObject:
        try:
            response = self.client.get_object(Bucket=bucket, Key=key)
        except ClientError as e:
            if _not_found(e):
                raise ObjectNotFound(f"{bucket}/{key}") from e
            raise
        with response["Body"] as body:
            data = body.read()
        return StoredObject(data=data, content_type=response.get("ContentType"), last_modified=response.get("LastModified"))

    def put(self, bucket: str, key: str, data: bytes, content_type: str) -> None:
        self.client.put_object(Bucket=bucket, Key=key, Body=data, ContentType=content_type)

    def exists(self, bucket: str, key: str) -> bool:
        try:
            self.client.head_object(Bucket=bucket, Key=key)
            return True
        except ClientError as e:
            if _not_found(e):
                return False
            raise

    def copy(self, bucket: str, source_key: str, target_key: str) -> None:
        self.client.copy_object(Bucket=bucket, Key=target_key, CopySource={"Bucket": bucket, "Key": source_key})

    def delete(self, bucket: str, key: str) -> None:
        self.client.delete_object(Bucket=bucket, Key=key)

    def bucket_exists(self, bucket: str) -> bool:
        try:
            self.client.head_bucket(Bucket=bucket)
            return True
        except ClientError as e:
            if _not_found(e):
                return False
            raise

    def ensure_bucket(self) -> None:
        """STORAGE_MANAGE_BUCKET 이 켜져 있으면 버킷을 만들고, STORAGE_PUBLIC_READ 면 익명 읽기 정책을 건다."""
        bucket = self.config.bucket
        if not self.config.manage_bucket or self.bucket_exists(bucket):
            return
        self.client.create_bucket(Bucket=bucket)
        if self.config.public_read:
            self.client.put_bucket_policy(Bucket=bucket, Policy=_public_read_policy(bucket))
        logger.info(f"Created bucket: {bucket} (public read: {self.config.public_read})")


def _public_read_policy(bucket: str) -> str:
    return json.dumps({
        "Version": "2012-10-17",
        "Statement": [{"Effect": "Allow", "Principal": {"AWS": "*"}, "Action": ["s3:GetObject"],
                       "Resource": [f"arn:aws:s3:::{bucket}/*"]}],
    })


object_storage = S3ObjectStorage(storage_config)
