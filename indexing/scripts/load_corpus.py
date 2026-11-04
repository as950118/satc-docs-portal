"""Upload a corpus JSON to MinIO and index it through the real document processor.

For local verification only. It refuses to run unless every search store it would write to is empty,
so it cannot add test documents to a stack that already holds documents.

    cd indexing && python -m scripts.load_corpus --corpus tests/fixtures/dummy_corpus.json
"""
import argparse
import io
import json
import sys
import time

from app.config import settings
from app.services import document_processor as dp
from app.services.minio_client import minio_client
from app.services.queue_service import InternalQueueService, TaskStatus, set_queue_service
from app.services.elasticsearch_client import elasticsearch_client
from app.services.full_document_client import full_document_client


def object_name(doc):
    return f"documents/{doc['document_id']}/{doc['title']}.md"


def es_counts():
    counts = {}
    for index in (elasticsearch_client.index_name, full_document_client.index_name):
        es = elasticsearch_client.client
        counts[index] = es.count(index=index)["count"] if es.indices.exists(index=index) else 0
    return counts


def refuse_unless_empty(bucket):
    leftovers = {k: v for k, v in es_counts().items() if v}
    if any(True for _ in minio_client.client.list_objects(bucket, recursive=True)):
        leftovers[f"minio:{bucket}"] = "not empty"
    if leftovers:
        sys.exit(f"refusing to load: target stores are not empty {leftovers}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--corpus", required=True)
    parser.add_argument("--es-full-index-body", help="JSON body to create the full_documents index with before loading")
    args = parser.parse_args()

    bucket = settings.minio_bucket_name
    refuse_unless_empty(bucket)
    if args.es_full_index_body:
        body = json.load(open(args.es_full_index_body))
        elasticsearch_client.client.indices.create(index=full_document_client.index_name, body=body)

    docs = json.load(open(args.corpus))["documents"]
    for doc in docs:
        data = doc["content"].encode("utf-8")
        minio_client.client.put_object(bucket, object_name(doc), io.BytesIO(data), len(data), content_type="text/markdown")

    statuses = {}
    dp.update_document_status = lambda name, status="COMPLETE": statuses.__setitem__(name, status) or True
    # One worker: on an empty ES, concurrent first writes raced on index creation and 3 of 50 documents failed.
    queue = InternalQueueService(max_workers=1)
    set_queue_service(queue)
    queue.register_task_handler("process_document", dp.process_document_handler)
    queue.start()
    task_ids = [queue.submit_task("process_document", {"bucket_name": bucket, "object_name": object_name(d), "metadata": {}}) for d in docs]
    deadline = time.time() + 300
    while time.time() < deadline:
        tasks = [queue.get_task_status(t) for t in task_ids]
        if all(t.status in (TaskStatus.COMPLETED, TaskStatus.FAILED) for t in tasks):
            break
        time.sleep(0.2)
    queue.stop()

    elasticsearch_client.client.indices.refresh(index="_all")
    failed = [t.task_id for t in tasks if t.status != TaskStatus.COMPLETED]
    print(json.dumps({"documents": len(docs), "task_failed": len(failed),
                      "status_calls": {s: sum(1 for v in statuses.values() if v == s) for s in set(statuses.values())},
                      "es_counts": es_counts()}, ensure_ascii=False))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
