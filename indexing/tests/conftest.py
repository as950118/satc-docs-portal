# These files are manual scripts that send live requests to a server on localhost:8000, so pytest must not collect them.
collect_ignore = ["test_bulk_delete.py", "test_clear_all.py", "test_delete.py", "test_unified_delete.py"]
