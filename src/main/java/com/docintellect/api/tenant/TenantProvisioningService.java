package com.docintellect.api.tenant;

import com.docintellect.api.model.ApiDtos;
import com.docintellect.api.model.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public ApiDtos.TenantRegistrationResponse provisionTenant(ApiDtos.TenantRegistrationRequest request) {
        if (tenantRepository.existsByTenantId(request.getTenantId())) {
            throw new IllegalArgumentException("Tenant already exists: " + request.getTenantId());
        }

        String schemaName = "tenant_" + request.getTenantId().toLowerCase().replaceAll("[^a-z0-9]", "_");
        Tenant.Tier tier = Tenant.Tier.valueOf(request.getTier().toUpperCase());

        createTenantSchema(schemaName);

        Tenant tenant = Tenant.builder()
                .tenantId(request.getTenantId())
                .name(request.getName())
                .schemaName(schemaName)
                .tier(tier)
                .active(true)
                .createdAt(Instant.now())
                .build();

        tenantRepository.save(tenant);
        log.info("Provisioned tenant {} with schema {}", request.getTenantId(), schemaName);

        return ApiDtos.TenantRegistrationResponse.builder()
                .tenantId(tenant.getTenantId())
                .schemaName(schemaName)
                .tier(tier.name())
                .createdAt(tenant.getCreatedAt())
                .build();
    }

    private void createTenantSchema(String schemaName) {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS %s.documents (
                id UUID PRIMARY KEY,
                tenant_id VARCHAR(100) NOT NULL,
                filename VARCHAR(500) NOT NULL,
                file_size_bytes BIGINT,
                mime_type VARCHAR(100),
                status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
                job_id UUID NOT NULL,
                chunk_count INT,
                error_message TEXT,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMPTZ
            )
            """.formatted(schemaName));

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS %s.chunks (
                id UUID PRIMARY KEY,
                document_id UUID NOT NULL,
                chunk_index INT NOT NULL,
                chunk_text TEXT NOT NULL,
                token_count INT,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                FOREIGN KEY (document_id) REFERENCES %s.documents(id) ON DELETE CASCADE
            )
            """.formatted(schemaName, schemaName));

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS %s.embeddings (
                id UUID PRIMARY KEY,
                chunk_id UUID NOT NULL,
                document_id UUID NOT NULL,
                embedding vector(768) NOT NULL,
                model_name VARCHAR(200),
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                FOREIGN KEY (chunk_id) REFERENCES %s.chunks(id) ON DELETE CASCADE
            )
            """.formatted(schemaName, schemaName));

        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_%s_embeddings_vector
            ON %s.embeddings USING ivfflat (embedding vector_cosine_ops)
            WITH (lists = 100)
            """.formatted(schemaName.replace(".", "_"), schemaName));

        log.info("Schema and tables created for {}", schemaName);
    }
}
