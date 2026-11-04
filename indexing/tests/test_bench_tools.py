"""지연 측정 도구의 순수 부분. 합성 문서는 더미 코퍼스에서만 만들고 같은 seed 면 같은 문서가 나온다.

실행: cd indexing && python -m pytest tests/test_bench_tools.py
"""
import json
from itertools import islice

from scripts.bench_search import percentile
from scripts.load_synthetic import CORPUS, generate


def test_percentile_picks_the_nearest_rank():
    values = list(range(1, 101))
    assert (percentile(values, 50), percentile(values, 95), percentile(values, 100)) == (51, 95, 100)


def test_synthetic_documents_are_deterministic_and_near_the_target_length():
    first = list(islice(generate(200, 2000, seed=7), 200))
    assert first == list(islice(generate(200, 2000, seed=7), 200))
    average = sum(len(content) for _, _, content in first) / len(first)
    assert 1500 < average < 3000


def test_synthetic_documents_use_only_dummy_corpus_words():
    vocab = {w for d in json.loads(CORPUS.read_text())["documents"] for w in (d["title"] + " " + d["content"]).split()}
    for doc_id, title, content in islice(generate(50, 1000, seed=1), 50):
        words = {w for w in content.replace("#", " ").split()} - {doc_id}
        assert all(w in vocab or w.isdigit() for w in words)
