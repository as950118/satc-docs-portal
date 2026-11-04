"""Compare the portal document table with the PostgreSQL search tables, and with ES when it is reachable.

Exits 1 when a must-be-zero check is not zero or when the active target documents are not all indexed.
The portal database is the one SEARCH_PG_* points to.

    cd indexing && python -m scripts.check_search_consistency
    python -m scripts.check_search_consistency --no-es
"""
import argparse
import json
import sys
from typing import Dict, List, Optional, Set

import psycopg

from app.config import settings

ACTIVE = "coalesce(d.is_deleted, false) = false and coalesce(d.is_folder, false) = false"

COUNTS = {
    "targets": f"select count(*) from document d where {ACTIVE}",
    "indexed_targets": f"select count(*) from search_document s join document d on d.id = s.document_id where {ACTIVE}",
    "indexed_all": "select count(*) from search_document",
    "chunks": "select count(*) from search_chunk",
    "index_only": "select count(*) from search_document s left join document d on d.id = s.document_id where d.id is null",
}

MUST_BE_ZERO = {
    "deleted_still_indexed": "select count(*) from search_document s join document d on d.id = s.document_id where d.is_deleted = true",
    "chunks_without_document": "select count(*) from search_chunk c left join search_document s on s.document_id = c.document_id "
                               "where s.document_id is null",
    "duplicate_result_features": "select count(*) from (select metadata->>'feature_id' from search_document "
                                 "where metadata->>'doc_type' = 'result' group by 1 having count(*) > 1) dup",
}

UNINDEXED = (f"select d.id, d.title from document d left join search_document s on s.document_id = d.id "
             f"where {ACTIVE} and s.document_id is null order by d.id limit 20")


def check_postgres(conn) -> Dict:
    report = {name: conn.execute(sql).fetchone()[0] for name, sql in COUNTS.items()}
    report["must_be_zero"] = {name: conn.execute(sql).fetchone()[0] for name, sql in MUST_BE_ZERO.items()}
    report["unindexed_targets_sample"] = [list(r) for r in conn.execute(UNINDEXED).fetchall()]
    report["reindex_marks"] = dict(conn.execute("select target, count(*) from search_reindex_state group by 1").fetchall())
    return report


def check_es(conn, es_ids: Set[str], es_counts: Dict[str, int]) -> Dict:
    pg_ids = {r[0] for r in conn.execute("select document_id from search_document")}
    deleted = {r[0] for r in conn.execute("select id from document where is_deleted = true")}
    return {**es_counts,
            "only_in_es": len(es_ids - pg_ids), "only_in_es_sample": sorted(es_ids - pg_ids)[:20],
            "only_in_postgres": len(pg_ids - es_ids), "only_in_postgres_sample": sorted(pg_ids - es_ids)[:20],
            "deleted_still_in_es": len(es_ids & deleted)}


def failed(report: Dict) -> List[str]:
    reasons = [name for name, value in report["must_be_zero"].items() if value]
    if report["targets"] != report["indexed_targets"]:
        reasons.append("targets != indexed_targets")
    return reasons


def es_snapshot():
    from elasticsearch.helpers import scan
    from app.services.elasticsearch_client import elasticsearch_client
    from app.services.full_document_client import full_document_client
    es = full_document_client.client
    counts = {f"es_{index}": es.count(index=index)["count"]
              for index in (full_document_client.index_name, elasticsearch_client.index_name)}
    ids = {hit["_source"]["document_id"] for hit in scan(es, index=full_document_client.index_name,
                                                         query={"query": {"match_all": {}}, "_source": ["document_id"]})}
    return ids, counts


def main(argv: Optional[list] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--no-es", action="store_true", help="skip the ES comparison")
    args = parser.parse_args(argv)
    from app.search.pg_store import PgSearchBackend, set_search_path
    conninfo = PgSearchBackend.conninfo(settings)
    with psycopg.connect(conninfo, autocommit=True) as conn:
        set_search_path(conn, settings.search_pg_schema)
        report = check_postgres(conn)
        if not args.no_es:
            report["es"] = check_es(conn, *es_snapshot())
    report["failed"] = failed(report)
    print(json.dumps(report, ensure_ascii=False, indent=1))
    return 1 if report["failed"] else 0


if __name__ == "__main__":
    sys.exit(main())
