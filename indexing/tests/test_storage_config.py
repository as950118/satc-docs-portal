"""환경변수로 저장소 설정이 어떻게 풀리는지 확인한다. 백엔드 StorageConfigTest 와 같은 규칙이다.

실행: python -m pytest tests/test_storage_config.py
"""
import pytest

from app.config import Settings, resolve_storage_config

_NAMES = ["STORAGE_ENDPOINT", "STORAGE_REGION", "STORAGE_BUCKET", "STORAGE_ACCESS_KEY", "STORAGE_SECRET_KEY",
          "STORAGE_PATH_STYLE", "STORAGE_MANAGE_BUCKET", "STORAGE_PUBLIC_READ",
          "MINIO_ENDPOINT", "MINIO_ACCESS_KEY", "MINIO_SECRET_KEY", "MINIO_SECURE", "MINIO_BUCKET_NAME"]


@pytest.fixture
def resolve(monkeypatch):
    def _resolve(**env):
        for name in _NAMES:
            monkeypatch.delenv(name, raising=False)
        for name, value in env.items():
            monkeypatch.setenv(name, value)
        return resolve_storage_config(Settings(_env_file=None))
    return _resolve


def test_without_environment_it_is_the_local_minio_of_before(resolve):
    config = resolve()

    assert config.endpoint == "http://localhost:9000"
    assert config.region == "us-east-1"
    assert config.bucket == "docs"
    assert (config.access_key, config.secret_key) == ("minioadmin", "minioadmin")
    assert (config.path_style, config.manage_bucket, config.public_read) == (True, True, True)


def test_legacy_minio_variables_still_work(resolve):
    config = resolve(MINIO_ENDPOINT="http://minio:9000", MINIO_SECURE="true", MINIO_ACCESS_KEY="ak",
                     MINIO_SECRET_KEY="sk", MINIO_BUCKET_NAME="cmp-docs")

    assert config.endpoint == "https://minio:9000"
    assert config.bucket == "cmp-docs"
    assert (config.access_key, config.secret_key) == ("ak", "sk")


def test_storage_variables_win_and_keep_the_endpoint_path(resolve):
    config = resolve(MINIO_ENDPOINT="minio:9000", MINIO_ACCESS_KEY="old", MINIO_BUCKET_NAME="old-bucket",
                     STORAGE_ENDPOINT="https://ref.storage.supabase.co/storage/v1/s3/", STORAGE_REGION="ap-northeast-2",
                     STORAGE_BUCKET="satc-docs", STORAGE_ACCESS_KEY="ak", STORAGE_SECRET_KEY="sk",
                     STORAGE_MANAGE_BUCKET="false", STORAGE_PUBLIC_READ="FALSE")

    assert config.endpoint == "https://ref.storage.supabase.co/storage/v1/s3"
    assert config.region == "ap-northeast-2"
    assert config.bucket == "satc-docs"
    assert config.access_key == "ak"
    assert (config.path_style, config.manage_bucket, config.public_read) == (True, False, False)


def test_blank_storage_variables_fall_back_like_unset_ones(resolve):
    blanks = {name: "" for name in _NAMES if name.startswith("STORAGE_")}

    config = resolve(MINIO_ENDPOINT="minio:9000", **blanks)

    assert config.endpoint == "http://minio:9000"
    assert config.bucket == "docs"
    assert config.access_key == "minioadmin"
    assert config.manage_bucket is True


@pytest.mark.parametrize("env, name", [
    ({"STORAGE_ENDPOINT": "minio:9000"}, "STORAGE_ENDPOINT"),
    ({"STORAGE_ENDPOINT": "https://x.example/s3?a=1"}, "STORAGE_ENDPOINT"),
    ({"STORAGE_BUCKET": "Docs_1"}, "STORAGE_BUCKET"),
    ({"STORAGE_MANAGE_BUCKET": "yes"}, "STORAGE_MANAGE_BUCKET"),
    ({"MINIO_SECRET_KEY": ""}, "STORAGE_SECRET_KEY"),
])
def test_invalid_values_stop_startup_naming_the_variable(resolve, env, name):
    with pytest.raises(ValueError, match=name):
        resolve(**env)


def test_config_text_never_shows_the_secret(resolve):
    assert "very-secret-value" not in repr(resolve(STORAGE_SECRET_KEY="very-secret-value"))
