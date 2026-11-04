-- Search index tables written by the indexing service. Every statement is idempotent.
-- No foreign key to the portal document table: indexing may write before or after the portal,
-- a delete race must not fail an index write, and index-only documents have no document row.

create table if not exists search_document (
    document_id text primary key,
    title       text not null default '',
    content     text not null default '',
    metadata    jsonb not null default '{}',
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create table if not exists search_chunk (
    chunk_id    text primary key,
    document_id text not null,
    source_file text not null,
    title       text not null default '',
    content     text not null,
    metadata    jsonb not null default '{}',
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create index if not exists idx_search_chunk_document_id on search_chunk (document_id);
create index if not exists idx_search_chunk_source_file on search_chunk (source_file);

-- A store that missed a write during dual writing. target is the store that must be rewritten.
create table if not exists search_reindex_state (
    document_id text not null,
    target      text not null check (target in ('es', 'postgres')),
    reason      text,
    marked_at   timestamptz not null default now(),
    primary key (document_id, target)
);
