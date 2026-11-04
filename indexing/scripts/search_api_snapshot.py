"""Call the search APIs in process and save the responses with the highlight rules they show.

Run it after scripts.load_corpus has loaded tests/fixtures/dummy_corpus.json. The configured
SEARCH_BACKEND decides which store answers, so the same query list snapshots either store.

    cd indexing && python -m scripts.search_api_snapshot --out tests/fixtures/es_search_snapshot.json
"""
import argparse
import html
import json
import re

from fastapi.testclient import TestClient

from app.config import settings
from app.main import app

LONG_DOC = "eeeeeeee-0000-4000-8000-000000000004"
LONG_DOC_SOURCE = f"documents/{LONG_DOC}/검색 품질 점검 기록.md"

CALLS = [
    ("documents 코딩 스타일", "/api/v1/search-documents", {"q": "코딩 스타일"}),
    ("documents 코딩 스타일을", "/api/v1/search-documents", {"q": "코딩 스타일을"}),
    ("documents 발췌", "/api/v1/search-documents", {"q": "발췌"}),
    ("documents 점검", "/api/v1/search-documents", {"q": "점검"}),
    ("documents 온보딩 (title only)", "/api/v1/search-documents", {"q": "온보딩"}),
    ("documents 빈 본문 (empty body)", "/api/v1/search-documents", {"q": "빈 본문"}),
    ("documents 굵은 글씨 (html)", "/api/v1/search-documents", {"q": "굵은 글씨"}),
    ("documents docker (case)", "/api/v1/search-documents", {"q": "docker"}),
    ("documents no hit", "/api/v1/search-documents", {"q": "zzqxj없는말"}),
    ("documents 트랜잭션 size 3", "/api/v1/search-documents", {"q": "트랜잭션", "size": 3}),
    ("documents 브랜치 from 1 size 2", "/api/v1/search-documents", {"q": "브랜치", "from": 1, "size": 2}),
    ("chunks 발췌", "/api/v1/search", {"q": "발췌"}),
    ("chunks 발췌 source_file", "/api/v1/search", {"q": "발췌", "source_file": LONG_DOC_SOURCE}),
    ("chunks 점검 (many matches in one chunk)", "/api/v1/search", {"q": "점검"}),
    ("chunks 굵은 글씨 (html)", "/api/v1/search", {"q": "굵은 글씨"}),
    ("chunks no hit", "/api/v1/search", {"q": "zzqxj없는말"}),
    ("document found", f"/api/v1/documents/{LONG_DOC}", {}),
    ("document missing", "/api/v1/documents/ffffffff-0000-4000-8000-000000000000", {}),
]


def strip_em(fragment):
    """Original text of a fragment: no <em> tags, and an escaped entity counts as the one character it stands for."""
    return html.unescape(re.sub(r"</?em>", "", fragment))


def observe(responses):
    """Summarise the highlight format each endpoint returned."""
    out = {}
    for endpoint in ("/api/v1/search-documents", "/api/v1/search"):
        fields, empty_highlights = {}, 0
        for name, call in responses.items():
            if call["path"] != endpoint or call["status"] != 200:
                continue
            for item in call["body"]["results"]:
                if not item["highlight"]:
                    empty_highlights += 1
                for field, fragments in item["highlight"].items():
                    f = fields.setdefault(field, {"max_fragments": 0, "max_len_without_em": 0, "tags": set()})
                    f["max_fragments"] = max(f["max_fragments"], len(fragments))
                    f["max_len_without_em"] = max([f["max_len_without_em"]] + [len(strip_em(x)) for x in fragments])
                    f["tags"].update(re.findall(r"</?[a-z]+>", "".join(fragments)))
        out[endpoint] = {"fields": {k: {**v, "tags": sorted(v["tags"])} for k, v in fields.items()},
                         "results_with_empty_highlight": empty_highlights}
    html = responses["documents 굵은 글씨 (html)"]["body"]["results"]
    fragments = [x for r in html for x in r["highlight"].get("content", [])]
    out["html_in_content_fragments"] = ("raw" if any("<b>" in x for x in fragments)
                                        else "escaped" if any("&lt;b&gt;" in x for x in fragments) else "no fragment")
    out["zero_hit_max_score"] = {k: responses[k]["body"]["max_score"] for k in ("documents no hit", "chunks no hit")}
    title_only = responses["documents 온보딩 (title only)"]["body"]["results"]
    out["title_only_highlight_keys"] = [sorted(r["highlight"]) for r in title_only]
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    responses = {}
    with TestClient(app) as client:
        for name, path, params in CALLS:
            r = client.get(path, params=params)
            responses[name] = {"path": path.split("/documents/")[0] + "/documents/{id}" if "/documents/" in path else path,
                               "params": params, "status": r.status_code, "body": r.json()}
    snapshot = {"search_backend": settings.search_backend,
                "observations": observe(responses), "responses": responses}
    with open(args.out, "w") as f:
        json.dump(snapshot, f, ensure_ascii=False, indent=1)
    print(json.dumps(snapshot["observations"], ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
