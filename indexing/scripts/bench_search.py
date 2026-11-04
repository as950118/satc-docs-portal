"""Server-side latency of PgSearchBackend.search_documents (AC-7: p95 <= 200 ms at 10,000 documents).

The timed region is the in-process call: the SQL round trip, excerpt building and response conversion. It
excludes the client to indexing hop. Each run times every query `--repeat` times after `--warmup` discarded
calls. Runs alternate between the targets so warmup and drift favour neither. Every run reports p50, p95, max,
errors and queries with no result, plus the round trip of `select 1` so the transport share is visible. After
the runs, EXPLAIN (ANALYZE, BUFFERS) of the slowest queries splits database time from application time.

    cd indexing && python -m scripts.bench_search --queries scripts/querysets/dummy_ko.yaml \\
        --target 'pgroonga=host=... port=... dbname=... user=... password=...' \\
        --target 'trgm=host=... dbname=...' --out bench.json
"""
import argparse
import json
import statistics
import sys
import time
from typing import Dict, List

import yaml
from psycopg_pool import ConnectionPool

from app.config import settings
from app.search.pg_store import PgSearchBackend, pgroonga_terms

P95_LIMIT_MS = 200


def percentile(values: List[float], p: float) -> float:
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(round(p / 100 * (len(ordered) - 1))))]


def open_store(text_index: str, conninfo: str) -> PgSearchBackend:
    threshold = str(settings.search_pg_trgm_threshold)

    def configure(conn):
        conn.execute("select set_config('pg_trgm.word_similarity_threshold', %s, false)", (threshold,))
        conn.commit()

    pool = ConnectionPool(conninfo, min_size=1, max_size=2, open=True, configure=configure, name=f"bench-{text_index}")
    pool.wait(timeout=30)
    store = PgSearchBackend(pool, text_index)
    missing = store.missing_indexes()
    if missing:
        sys.exit(f"{text_index} target is missing its indexes {missing}")
    return store


def one_run(store: PgSearchBackend, queries: List[str], repeat: int, warmup: int, size: int) -> Dict:
    samples, per_query, errors, empty = [], {}, 0, 0
    for q in queries:
        for _ in range(warmup):
            store.search_documents(q, size, 0)
        times, result = [], None
        for _ in range(repeat):
            started = time.perf_counter()
            try:
                result = store.search_documents(q, size, 0)
            except Exception:
                errors += 1
                continue
            times.append((time.perf_counter() - started) * 1000)
        if result is None or not result["results"]:
            empty += 1
        samples += times
        per_query[q] = statistics.median(times) if times else None
    with store.pool.connection() as conn:
        rtt = []
        for _ in range(50):
            started = time.perf_counter()
            conn.execute("select 1").fetchone()
            rtt.append((time.perf_counter() - started) * 1000)
    return {"p50_ms": round(statistics.median(samples), 2), "p95_ms": round(percentile(samples, 95), 2),
            "max_ms": round(max(samples), 2), "samples": len(samples), "errors": errors, "queries_without_result": empty,
            "select1_rtt_p50_ms": round(statistics.median(rtt), 3), "per_query_median_ms": per_query}


def explain(store: PgSearchBackend, query: str, size: int) -> Dict:
    strategy = store.strategy
    sql = (f"explain (analyze, buffers, format json) select t.document_id, t.title, t.content, t.metadata, "
           f"{strategy.score} as score, count(*) over () as total, max({strategy.score}) over () as top "
           f"from search_document t where {strategy.match} order by score desc, 1 limit %(size)s offset 0")
    with store.pool.connection() as conn:
        plan = conn.execute(sql, {"q": query, "terms": pgroonga_terms(query), "size": size}).fetchone()[0][0]
    nodes, stack = [], [plan["Plan"]]
    while stack:
        node = stack.pop()
        nodes.append(f"{node['Node Type']}{' on ' + node['Index Name'] if 'Index Name' in node else ''} "
                     f"rows={node.get('Actual Rows')} ms={node.get('Actual Total Time')}")
        stack += node.get("Plans", [])
    return {"execution_ms": plan["Execution Time"], "planning_ms": plan["Planning Time"], "nodes": nodes}


def server_facts(store: PgSearchBackend) -> Dict:
    with store.pool.connection() as conn:
        facts = {name: conn.execute(f"show {name}").fetchone()[0] for name in ("shared_buffers", "work_mem", "server_version")}
        docs, avg_len = conn.execute("select count(*), round(avg(length(content))) from search_document").fetchone()
    return {**facts, "documents": docs, "avg_content_chars": int(avg_len or 0)}


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--queries", required=True)
    parser.add_argument("--target", action="append", required=True, help="<pgroonga|trgm>=<libpq conninfo>")
    parser.add_argument("--runs", type=int, default=5)
    parser.add_argument("--repeat", type=int, default=20)
    parser.add_argument("--warmup", type=int, default=5)
    parser.add_argument("--size", type=int, default=10)
    parser.add_argument("--explain-top", type=int, default=3)
    parser.add_argument("--out", required=True)
    args = parser.parse_args(argv)

    queries = [q["query"] for q in yaml.safe_load(open(args.queries))["queries"]]
    stores = {name: open_store(name, conninfo) for name, conninfo in (t.split("=", 1) for t in args.target)}
    runs = {name: [] for name in stores}
    for run in range(args.runs):
        order = list(stores) if run % 2 == 0 else list(reversed(stores))
        for name in order:
            result = one_run(stores[name], queries, args.repeat, args.warmup, args.size)
            runs[name].append(result)
            print(f"run {run + 1} {name}: p50 {result['p50_ms']} ms, p95 {result['p95_ms']} ms, max {result['max_ms']} ms, "
                  f"errors {result['errors']}, empty {result['queries_without_result']}", file=sys.stderr)

    report = {"queries": len(queries), "repeat": args.repeat, "warmup": args.warmup, "size": args.size, "targets": {}}
    for name, results in runs.items():
        p95s = [r["p95_ms"] for r in results]
        medians = {q: statistics.median(r["per_query_median_ms"][q] for r in results) for q in queries}
        slowest = sorted(medians, key=medians.get, reverse=True)[:args.explain_top]
        report["targets"][name] = {
            "server": server_facts(stores[name]),
            "p95_ms_per_run": p95s, "p95_range_ms": [min(p95s), max(p95s)],
            "p50_ms_per_run": [r["p50_ms"] for r in results], "max_ms_per_run": [r["max_ms"] for r in results],
            "errors": sum(r["errors"] for r in results),
            "queries_without_result": results[0]["queries_without_result"],
            "select1_rtt_p50_ms": [r["select1_rtt_p50_ms"] for r in results],
            "p95_within_limit_every_run": all(p <= P95_LIMIT_MS for p in p95s),
            "slowest_queries": {q: {"median_ms": round(medians[q], 2), "explain": explain(stores[name], q, args.size)} for q in slowest},
        }
    with open(args.out, "w") as f:
        json.dump(report, f, ensure_ascii=False, indent=1)
    print(json.dumps({name: {k: t[k] for k in ("p95_ms_per_run", "p95_range_ms", "errors", "queries_without_result",
                                               "p95_within_limit_every_run")} | {"documents": t["server"]["documents"]}
                      for name, t in report["targets"].items()}, ensure_ascii=False))
    return 0 if all(t["p95_within_limit_every_run"] and not t["errors"] for t in report["targets"].values()) else 1


if __name__ == "__main__":
    sys.exit(main())
