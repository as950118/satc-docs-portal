create extension if not exists pg_trgm;

create index if not exists idx_search_document_title_trgm on search_document using gin (title gin_trgm_ops);
create index if not exists idx_search_document_content_trgm on search_document using gin (content gin_trgm_ops);
create index if not exists idx_search_chunk_title_trgm on search_chunk using gin (title gin_trgm_ops);
create index if not exists idx_search_chunk_content_trgm on search_chunk using gin (content gin_trgm_ops);
