"""품질 비교 도구(AC-2)의 판정 규칙. 저장소 없이 순위 목록으로 확인한다.

실행: cd indexing && python -m pytest tests/test_compare_search.py
"""
from scripts.compare_search import evaluate, failed_pairs, load_queries, query_set_problems, verdict


def q(query, expected, category="정확", pair_id=None):
    return {"query": query, "expected_doc_ids": expected, "category": category, "pair_id": pair_id}


def test_dummy_query_sets_pass_the_size_and_category_check():
    assert query_set_problems(load_queries("scripts/querysets/dummy_ko.yaml")) == []
    assert query_set_problems(load_queries("scripts/querysets/poc49.yaml")) == []


def test_small_or_unbalanced_query_set_is_rejected():
    base = [q("a", ["1"], "조사"), q("b", ["1"], "한영혼용"), q("c", ["1"], "짧은키워드")]
    assert query_set_problems(base + [q(f"x{n}", ["1"]) for n in range(17)]) == []
    assert query_set_problems(base + [q(f"x{n}", ["1"]) for n in range(16)]) == ["query set has 19 queries, needs 20"]
    no_short = [x for x in base if x["category"] != "짧은키워드"] + [q(f"x{n}", ["1"]) for n in range(18)]
    assert query_set_problems(no_short) == ["query set has no 짧은키워드 query"]


def test_hit_at_k_and_mrr_use_the_rank_of_the_first_expected_document():
    queries = [q("a", ["A"]), q("b", ["B"]), q("c", ["C"]), q("d", ["D"])]
    results = {"a": ["A"], "b": ["x", "x", "B"], "c": ["x", "x", "x", "x", "C"], "d": ["x"]}
    summary = evaluate(lambda text: results[text], queries)["summary"]
    assert summary == {"hit@1": 0.25, "hit@3": 0.5, "hit@5": 0.75, "mrr": round((1 + 1 / 3 + 1 / 5) / 4, 3)}


def test_pair_fails_when_either_member_misses_the_top_five():
    queries = [q("코딩 스타일", ["A"], pair_id="p1"), q("코딩 스타일을", ["A"], pair_id="p1"), q("롤백", ["B"], pair_id="p2")]
    assert failed_pairs(queries, [1, None, 2]) == ["p1"]
    assert failed_pairs(queries, [1, 5, 2]) == []


def summary(hit5):
    return {"summary": {"hit@5": hit5}}


def test_verdict_requires_the_floor_and_at_least_es():
    assert verdict(summary(0.9), summary(0.96), []) == []
    assert verdict(summary(0.9), summary(0.94), []) == ["postgres hit@5 0.94 < 0.95"]
    assert verdict(summary(0.98), summary(0.96), []) == ["postgres hit@5 0.96 < es hit@5 0.98"]
    assert verdict(summary(0.9), summary(1.0), ["p1"]) == ["pairs failed on postgres: ['p1']"]
