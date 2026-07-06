create extension if not exists vector;

-- RAG knowledge chunks. Outputs from all chunking strategies coexist by chunking_strategy.
create table if not exists document_chunks (
  id bigserial primary key,
  chunking_strategy text not null,
  topic_id bigint,
  source_file text not null,
  heading text,
  content text not null,
  source text,
  char_length int not null,
  embedding vector(1024) not null,
  created_at timestamptz not null default now()
);

create index if not exists idx_document_chunks_embedding
  on document_chunks using hnsw (embedding vector_cosine_ops);

create index if not exists idx_document_chunks_strategy_topic
  on document_chunks (chunking_strategy, topic_id);

-- Quiz similarity index. MySQL remains the source of truth; this table is rebuildable.
create table if not exists quiz_embeddings (
  quiz_id bigint primary key,
  topic_id bigint not null,
  question_title text not null,
  embedding vector(1024) not null,
  updated_at timestamptz not null default now()
);

create index if not exists idx_quiz_embeddings_embedding
  on quiz_embeddings using hnsw (embedding vector_cosine_ops);

create index if not exists idx_quiz_embeddings_topic
  on quiz_embeddings (topic_id);
