# Test Suite — doc-intelligence-api

> **Covered in v0.2**

This directory will contain unit and integration test coverage. Scenarios planned for the next sprint:

## Unit Tests

### TenantResolutionFilter
- Missing `X-Tenant-ID` header returns 400
- Unknown tenant ID returns 401
- Inactive tenant returns 401
- Valid tenant populates TenantContext correctly
- TenantContext is cleared after request completes

### TextChunker
- Empty string returns empty list
- Short text (< chunk size) returns single chunk
- Long text produces correctly overlapping windows
- Special characters and whitespace normalization

### EmbeddingService
- Vertex AI happy path returns 768-dim vector
- Vertex AI timeout triggers OpenAI fallback
- Vertex AI 5xx triggers OpenAI fallback
- Missing OpenAI key throws when fallback needed
- Vector dimensionality is normalized to 768

### GenerationService
- Vertex AI happy path returns answer text
- Timeout after 10s triggers OpenAI fallback
- INSUFFICIENT_CONTEXT marker detection

### RAGQueryService
- Empty retrieval results returns grounded=false response
- Similarity below threshold returns grounded=false
- Valid chunks produce grounded=true response with sources
- Source documents truncated to 200 chars

### RateLimitInterceptor
- FREE tier: 21st request in window returns 429
- PRO tier: 201st request in window returns 429
- `Retry-After` header is present on 429
- Window resets after expiry

## Integration Tests

### Document Ingestion Pipeline (TestContainers)
- Upload PDF → 202 Accepted with jobId
- Poll status → transitions QUEUED → PROCESSING → COMPLETED
- Chunks and embeddings persisted in tenant schema
- Upload non-PDF returns 400
- Upload >50MB returns 413

### RAG Query Pipeline (TestContainers + WireMock)
- Query against ingested document returns grounded answer
- Query with no documents returns grounded=false
- Vertex AI mocked as 503 → OpenAI fallback fires
- Rate limit counter increments in Redis

### Multi-Tenancy Isolation (TestContainers)
- Tenant A cannot see Tenant B's documents via query
- Tenant A cannot see Tenant B's embeddings

### Tenant Provisioning
- New tenant creates isolated schema
- Duplicate tenant ID returns 422
- Invalid tier returns 400
