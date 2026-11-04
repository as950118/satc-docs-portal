# 아래 파일은 http://localhost:8000 에 떠 있는 서버로 실제 삭제 요청을 보내는 수동 스크립트다.
# pytest 가 모으면 개발 PC 에서 돌고 있는 Indexing Server 의 색인을 지울 수 있어 수집하지 않는다.
collect_ignore = ["test_bulk_delete.py", "test_clear_all.py", "test_delete.py", "test_unified_delete.py"]
