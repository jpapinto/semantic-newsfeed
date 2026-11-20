-- Enable the pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create the primary articles table
CREATE TABLE articles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(512) NOT NULL,
    url VARCHAR(1024) NOT NULL UNIQUE,
    summary TEXT,
    category VARCHAR(50) NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

-- Index to speed up our existByUrl deduplication checks
CREATE INDEX idx_articles_url ON articles(url);

-- Embeddings are not stored in this table: Spring AI's PgVectorStore keeps them in its own
-- vector_store table, with an HNSW cosine index (see spring.ai.vectorstore.pgvector in application.yml).
-- Each vector_store row carries the article's UUID in its metadata ("articleId").