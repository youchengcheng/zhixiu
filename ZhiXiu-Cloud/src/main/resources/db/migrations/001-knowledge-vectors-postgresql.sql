CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS repair_knowledge_vectors (
    id        TEXT PRIMARY KEY,
    content   TEXT NOT NULL,
    metadata  JSON NOT NULL,
    embedding VECTOR(1024) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_repair_knowledge_vectors_knowledge_id
    ON repair_knowledge_vectors ((metadata ->> 'knowledgeId'));
