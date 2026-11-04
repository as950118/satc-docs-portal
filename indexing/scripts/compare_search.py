"""Compare ES and PostgreSQL document search on one query set and fail when PostgreSQL misses the AC-2 bar.

Query set YAML: {queries: [{query, expected_doc_ids: [..], category, pair_id}]}. Both stores must hold the same
document_id set. PostgreSQL uses SEARCH_PG_TEXT_INDEX and must already have that strategy's indexes.

Exit 1 when the query set is too small, the document sets differ, PostgreSQL hit@5 < 0.95 or below ES hit@5,
or a pair (a query with and without a particle) does not find its expected document in the top 5 on PostgreSQL.

    cd indexing && python -m scripts.compare_search --queries scripts/querysets/dummy_ko.yaml --out compare.json
"""
import argparse
import json
import sys
from collections import Counter, defaultdict
from typing import Callable, Dict, List, Optional

import yaml

KS = (1, 3, 5)
MIN_QUERIES = 20
REQUIRED_CATEGORIES = ({"조사", "어미"}, {"한영혼용"}, {"짧은키워드"})
HIT5_FLOOR = 0.95


def load_queries(path: str) -> List[Dict]:
    return yaml.safe_load(open(path))["queries"]


def query_set_problems(queries: List[Dict]) -> List[str]:
    problems = []
    if len(queries) < MIN_QUERIES:
        problems.append(f"query set has {len(queries)} queries, needs {MIN_QUERIES}")
    categories = Counter(q["category"] for q in queries)
    for group in REQUIRED_CATEGORIES:
        if not any(categories[c] for c in group):
            problems.append(f"query set has no {'/'.join(sorted(group))} query")
    return problems


def rank(ids: List[str], expected: List[str]) -> Optional[int]:
    return next((n + 1 for n, doc_id in enumerate(ids) if doc_id in expected), None)


def evaluate(search: Callable[[str], List[str]], queries: List[Dict]) -> Dict:
    ranks = [rank(search(q["query"]), q["expected_doc_ids"]) for q in queries]
    n = len(queries)
    summary = {f"hit@{k}": round(sum(1 for r in ranks if r and r <= k) / n, 3) for k in KS}
    summary["mrr"] = round(sum(1 / r for r in ranks if r) / n, 3)
    return {"summary": summary, "ranks": ranks}


def failed_pairs(queries: List[Dict], ranks: List[Optional[int]]) -> List[str]:
    pairs = defaultdict(list)
    for q, r in zip(queries, ranks):
        if q.get("pair_id"):
            pairs[q["pair_id"]].append(r)
    return sorted(pid for pid, rs in pairs.items() if not all(r and r <= 5 for r in rs))


def verdict(es: Dict, pg: Dict, pairs_failed: List[str]) -> List[str]:
    reasons = []
    if pg["summary"]["hit@5"] < HIT5_FLOOR:
        reasons.append(f"postgres hit@5 {pg['summary']['hit@5']} < {HIT5_FLOOR}")
    if pg["summary"]["hit@5"] < es["summary"]["hit@5"]:
        reasons.append(f"postgres hit@5 {pg['summary']['hit@5']} < es hit@5 {es['summary']['hit@5']}")
    if pairs_failed:
        reasons.append(f"pairs failed on postgres: {pairs_failed}")
    return reasons


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--queries", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--size", type=int, default=5, help="results per query; ranks beyond it count as misses")
    args = parser.parse_args(argv)

    queries = load_queries(args.queries)
    problems = query_set_problems(queries)
    if problems:
        print(json.dumps({"failed": problems}, ensure_ascii=False))
        return 1

    from elasticsearch.helpers import scan
    from app.config import settings
    from app.search.es_store import EsSearchBackend
    from app.search.pg_store import PgSearchBackend
    from app.services.full_document_client import full_document_client

    pg = PgSearchBackend.from_settings(settings)
    missing = pg.missing_indexes()
    if missing:
        print(json.dumps({"failed": [f"postgres is missing {settings.search_pg_text_index} indexes {missing}"]}))
        return 1
    es_client, es_index = full_document_client.client, full_document_client.index_name
    es_ids = {h["_source"]["document_id"] for h in scan(es_client, index=es_index, query={"query": {"match_all": {}}, "_source": ["document_id"]})}
    with pg.pool.connection() as conn:
        pg_ids = {r[0] for r in conn.execute("select document_id from search_document")}
    if es_ids != pg_ids:
        print(json.dumps({"failed": ["document sets differ"], "only_in_es": sorted(es_ids - pg_ids)[:20],
                          "only_in_postgres": sorted(pg_ids - es_ids)[:20]}, ensure_ascii=False))
        return 1

    es_store = EsSearchBackend()
    es = evaluate(lambda q: [r["document_id"] for r in es_store.search_documents(q, args.size, 0)["results"]], queries)
    pgr = evaluate(lambda q: [r["document_id"] for r in pg.search_documents(q, args.size, 0)["results"]], queries)
    pairs_failed = failed_pairs(queries, pgr["ranks"])
    reasons = verdict(es, pgr, pairs_failed)

    by_category = defaultdict(lambda: {"queries": 0, "es_hit@5": 0, "postgres_hit@5": 0})
    for q, re_, rp in zip(queries, es["ranks"], pgr["ranks"]):
        c = by_category[q["category"]]
        c["queries"] += 1
        c["es_hit@5"] += bool(re_ and re_ <= 5)
        c["postgres_hit@5"] += bool(rp and rp <= 5)
    result = {
        "query_set": args.queries, "documents": len(pg_ids), "queries": len(queries),
        "postgres_text_index": settings.search_pg_text_index,
        "summary": {"es": es["summary"], "postgres": pgr["summary"]},
        "by_category": by_category,
        "pairs_failed_on_postgres": pairs_failed,
        "failed": reasons,
        "per_query": [{"query": q["query"], "category": q["category"], "pair_id": q.get("pair_id"),
                       "es_rank": re_, "postgres_rank": rp} for q, re_, rp in zip(queries, es["ranks"], pgr["ranks"])],
        "es_index": {"mapping": es_client.indices.get_mapping(index=es_index).body,
                     "settings": es_client.indices.get_settings(index=es_index).body},
    }
    with open(args.out, "w") as f:
        json.dump(result, f, ensure_ascii=False, indent=1)
    print(json.dumps({k: result[k] for k in ("documents", "queries", "postgres_text_index", "summary", "pairs_failed_on_postgres", "failed")},
                     ensure_ascii=False))
    return 1 if reasons else 0


if __name__ == "__main__":
    sys.exit(main())
