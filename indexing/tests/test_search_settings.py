"""SEARCH_* 설정 검증. 잘못된 값은 시작을 거부하고 허용 값을 알린다.

실행: cd indexing && python -m pytest tests/test_search_settings.py
"""
import os
import subprocess
import sys
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.config import Settings

INDEXING_DIR = Path(__file__).resolve().parents[1]


def load(monkeypatch, **env):
    for key in ("SEARCH_BACKEND", "SEARCH_DUAL_WRITE", "SEARCH_PG_TEXT_INDEX"):
        monkeypatch.delenv(key, raising=False)
    for key, value in env.items():
        monkeypatch.setenv(key, value)
    return Settings(_env_file=None)


@pytest.mark.parametrize("raw, expected", [("es", "es"), ("postgres", "postgres"), (" ES ", "es"), ("", "es"), ("Postgres", "postgres")])
def test_search_backend_accepts_allowed_values_after_trim_and_lowercase(monkeypatch, raw, expected):
    assert load(monkeypatch, SEARCH_BACKEND=raw).search_backend == expected


def test_defaults_keep_current_deployment_on_es_without_postgres(monkeypatch):
    s = load(monkeypatch)
    assert (s.search_backend, s.search_dual_write, s.search_pg_text_index) == ("es", False, "pgroonga")
    assert s.uses_postgres is False


@pytest.mark.parametrize("env, allowed", [
    ({"SEARCH_BACKEND": "elastic"}, "es, postgres"),
    ({"SEARCH_DUAL_WRITE": "yes"}, "true, false"),
    ({"SEARCH_PG_TEXT_INDEX": "bigm"}, "pgroonga, trgm"),
])
def test_invalid_value_is_rejected_with_allowed_values(monkeypatch, env, allowed):
    with pytest.raises(ValidationError) as err:
        load(monkeypatch, **env)
    assert allowed in str(err.value)


@pytest.mark.parametrize("backend, dual, uses_pg", [("es", "false", False), ("es", "true", True), ("postgres", "true", True), ("postgres", "false", True)])
def test_postgres_is_used_only_when_it_is_searched_or_written(monkeypatch, backend, dual, uses_pg):
    assert load(monkeypatch, SEARCH_BACKEND=backend, SEARCH_DUAL_WRITE=dual).uses_postgres is uses_pg


def test_service_refuses_to_start_with_invalid_backend():
    env = {**os.environ, "SEARCH_BACKEND": "elastic"}
    proc = subprocess.run([sys.executable, "-c", "import app.main"], cwd=INDEXING_DIR, env=env,
                          capture_output=True, text=True, timeout=60)
    assert proc.returncode != 0
    assert "SEARCH_BACKEND 허용 값: es, postgres" in proc.stderr
