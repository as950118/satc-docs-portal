create extension if not exists pgroonga;

create index if not exists idx_search_document_pgroonga on search_document using pgroonga (title, content);
create index if not exists idx_search_chunk_pgroonga on search_chunk using pgroonga (title, content);

drop index if exists idx_search_document_title_trgm;
drop index if exists idx_search_document_content_trgm;
drop index if exists idx_search_chunk_title_trgm;
drop index if exists idx_search_chunk_content_trgm;
