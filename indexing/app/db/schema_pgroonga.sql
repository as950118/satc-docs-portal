create extension if not exists pgroonga;

create index if not exists idx_search_document_pgroonga on search_document using pgroonga (title, content);
create index if not exists idx_search_chunk_pgroonga on search_chunk using pgroonga (title, content);
