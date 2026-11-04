"""Fill an empty PostgreSQL search store with synthetic documents for latency measurement (AC-7).

Documents are generated from the dummy corpus only: sentences are sampled and about a third of their words are
swapped for random words of the corpus vocabulary, so exact sentence repeats are rarer than in the PoC.
Hit rates on this data mean nothing. Writes go through PgSearchBackend.index_document with the chunking of the
indexing queue. It refuses to write into a store that already has documents.

    cd indexing && python -m scripts.load_synthetic --docs 10000 --avg-chars 2000
"""
import argparse
import json
import random
import re
import statistics
import sys
import time
from pathlib import Path

from app.config import settings
from app.models.schemas import FullDocument
from app.search.pg_store import PgSearchBackend
from app.services.chunking_service import chunking_service

CORPUS = Path(__file__).resolve().parents[1] / "tests" / "fixtures" / "dummy_corpus.json"


def generate(n: int, avg_chars: int, seed: int):
    rnd = random.Random(seed)
    docs = json.loads(CORPUS.read_text())["documents"]
    sentences = [s.strip() for d in docs for s in re.split(r"(?<=[.])\s+|\n+", d["content"]) if len(s.strip()) > 20 and not s.startswith("#")]
    vocab = sorted({w for d in docs for w in (d["title"] + " " + d["content"]).split() if not w.startswith("#")})
    for i in range(n):
        target = max(200, int(rnd.lognormvariate(0, 0.5) * avg_chars))
        title = " ".join(rnd.sample(vocab, rnd.randint(2, 4))) + f" {i}"
        parts, size, section = [f"# {title}"], 0, 0
        while size < target:
            if size >= section * 600:
                section += 1
                parts.append(f"\n## {' '.join(rnd.sample(vocab, 2))}\n")
            words = [w if rnd.random() > 0.35 else rnd.choice(vocab) for w in rnd.choice(sentences).split()]
            sentence = " ".join(words)
            parts.append(sentence)
            size += len(sentence) + 1
        yield f"bbbbbbbb-0000-4000-8000-{i:012d}", title, "\n".join(parts)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--docs", type=int, default=10000)
    parser.add_argument("--avg-chars", type=int, default=2000)
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args(argv)

    store = PgSearchBackend.from_settings(settings)
    store.ensure_schema()
    if store.stats()["document_count"]:
        sys.exit("refusing to load: search_document is not empty")
    started, lengths, chunks_total = time.time(), [], 0
    for n, (doc_id, title, content) in enumerate(generate(args.docs, args.avg_chars, args.seed), start=1):
        name = f"documents/{doc_id}/{title}.md"
        metadata = {"bucket_name": "synthetic", "object_name": name, "document_id": doc_id, "document_title": title}
        chunks = chunking_service.chunk_document(content, name, metadata)
        store.index_document(FullDocument(document_id=doc_id, title=title, content=content, metadata=metadata), chunks)
        lengths.append(len(content))
        chunks_total += len(chunks)
        if n % 1000 == 0:
            print(f"loaded {n} in {time.time() - started:.0f}s", file=sys.stderr)
    with store.pool.connection() as conn:
        conn.execute("analyze search_document")
        conn.execute("analyze search_chunk")
        sizes = dict(conn.execute("select c.relname, pg_relation_size(c.oid) from pg_class c "
                                  "where c.relname like 'idx_search_%' or c.relname in ('search_document', 'search_chunk')").fetchall())
    print(json.dumps({"documents": len(lengths), "chunks": chunks_total, "avg_chars": round(statistics.mean(lengths)),
                      "median_chars": statistics.median(lengths), "seconds": round(time.time() - started, 1),
                      "text_index": settings.search_pg_text_index, "relation_bytes": sizes}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
