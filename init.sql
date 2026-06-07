-- doc-intelligence-api database initialization
-- Run automatically by Docker Compose on first start

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Public schema: tenant registry
CREATE TABLE IF NOT EXISTS public.tenants (
    tenant_id    VARCHAR(100) PRIMARY KEY,
    name         VARCHAR(500) NOT NULL,
    schema_name  VARCHAR(200) NOT NULL UNIQUE,
    tier         VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tenants_active ON public.tenants (tenant_id) WHERE active = TRUE;

-- Seed a demo tenant for quickstart (password: see .env.example)
-- Comment out in production
INSERT INTO public.tenants (tenant_id, name, schema_name, tier, active, created_at)
VALUES ('demo', 'Demo Tenant', 'tenant_demo', 'PRO', TRUE, NOW())
ON CONFLICT (tenant_id) DO NOTHING;

-- Create the demo tenant schema if using the seed above
CREATE SCHEMA IF NOT EXISTS tenant_demo;

CREATE TABLE IF NOT EXISTS tenant_demo.documents (
    id             UUID         PRIMARY KEY,
    tenant_id      VARCHAR(100) NOT NULL,
    filename       VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT,
    mime_type      VARCHAR(100),
    status         VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
    job_id         UUID         NOT NULL,
    chunk_count    INT,
    error_message  TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS tenant_demo.chunks (
    id           UUID  PRIMARY KEY,
    document_id  UUID  NOT NULL,
    chunk_index  INT   NOT NULL,
    chunk_text   TEXT  NOT NULL,
    token_count  INT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (document_id) REFERENCES tenant_demo.documents(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tenant_demo.embeddings (
    id           UUID   PRIMARY KEY,
    chunk_id     UUID   NOT NULL,
    document_id  UUID   NOT NULL,
    embedding    vector(768) NOT NULL,
    model_name   VARCHAR(200),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (chunk_id) REFERENCES tenant_demo.chunks(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tenant_demo_embeddings_vector
    ON tenant_demo.embeddings USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
