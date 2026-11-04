"""실제 S3 호환 저장소에 붙어 S3ObjectStorage 를 확인한다. 백엔드 StorageContractTest 와 같은 계약이다.

STORAGE_CONTRACT_TEST=true 와 STORAGE_* 환경변수가 있을 때만 돈다. 객체는 compat-test/ 아래에만 만들고 지운다.
STORAGE_CONTRACT_REJECTS_NON_ASCII=true 면 한글 키를 거부하는 저장소(Supabase)로 본다.

실행: STORAGE_CONTRACT_TEST=true STORAGE_ENDPOINT=... python -m pytest tests/test_object_storage_contract.py
"""
import os
import uuid

import pytest
from botocore.exceptions import ClientError

from app.config import Settings, resolve_storage_config
from app.services.object_storage import ObjectNotFound, S3ObjectStorage

pytestmark = pytest.mark.skipif(os.environ.get("STORAGE_CONTRACT_TEST") != "true", reason="STORAGE_CONTRACT_TEST=true 일 때만")

BODY = "# 계약 테스트\nbody".encode("utf-8")


@pytest.fixture
def storage():
    config = resolve_storage_config(Settings(_env_file=None))
    storage = S3ObjectStorage(config)
    prefix = f"compat-test/{uuid.uuid4()}/"
    written = []

    def key(name):
        written.append(prefix + name)
        return prefix + name

    yield storage, config.bucket, key
    for k in written:
        try:
            storage.delete(config.bucket, k)
        except Exception:
            pass


def test_put_get_exists_copy_delete_with_ascii_keys(storage):
    s3, bucket, key = storage
    doc_id = str(uuid.uuid4())
    source, target = key(f"documents/{doc_id}/{doc_id}.md"), key(f"documents/{doc_id}/{doc_id}-copy.md")
    s3.ensure_bucket()
    assert s3.bucket_exists(bucket)

    s3.put(bucket, source, BODY, "text/markdown")
    s3.copy(bucket, source, target)

    assert s3.exists(bucket, source) and s3.exists(bucket, target)
    stored = s3.get(bucket, target)
    assert stored.data == BODY
    assert stored.content_type == "text/markdown"
    s3.delete(bucket, source)
    s3.delete(bucket, target)
    assert not s3.exists(bucket, source)
    with pytest.raises(ObjectNotFound):
        s3.get(bucket, target)


def test_hangul_title_keys_depend_on_the_provider(storage):
    s3, bucket, key = storage
    hangul = key(f"documents/{uuid.uuid4()}/한글 제목 (계약).md")

    if os.environ.get("STORAGE_CONTRACT_REJECTS_NON_ASCII") == "true":
        # 거부되는 키는 HEAD 도 404 가 아니라 400 이라 exists 로 확인할 수 없다
        with pytest.raises(ClientError) as rejected:
            s3.put(bucket, hangul, BODY, "text/markdown")
        assert rejected.value.response["Error"]["Code"] == "InvalidKey"
        assert rejected.value.response["ResponseMetadata"]["HTTPStatusCode"] == 400
    else:
        s3.put(bucket, hangul, BODY, "text/markdown")
        assert s3.get(bucket, hangul).data == BODY
