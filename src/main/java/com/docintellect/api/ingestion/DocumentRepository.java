package com.docintellect.api.ingestion;

import com.docintellect.api.model.Document;
import com.docintellect.api.tenant.TenantAwareJdbcTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DocumentRepository {

    private final TenantAwareJdbcTemplate tenantJdbc;

    public void save(Document doc) {
        tenantJdbc.execute("""
            INSERT INTO documents (id, tenant_id, filename, file_size_bytes, mime_type,
                                   status, job_id, chunk_count, error_message, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                chunk_count = EXCLUDED.chunk_count,
                error_message = EXCLUDED.error_message,
                updated_at = EXCLUDED.updated_at
            """,
                doc.getId(), doc.getTenantId(), doc.getFilename(), doc.getFileSizeBytes(),
                doc.getMimeType(), doc.getStatus().name(), doc.getJobId(), doc.getChunkCount(),
                doc.getErrorMessage(), doc.getCreatedAt(), doc.getUpdatedAt()
        );
    }

    public Optional<Document> findByJobId(UUID jobId) {
        var results = tenantJdbc.query(
                "SELECT * FROM documents WHERE job_id = ?",
                new DocumentRowMapper(),
                jobId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public void updateStatus(UUID id, Document.ProcessingStatus status, String errorMessage) {
        tenantJdbc.execute(
                "UPDATE documents SET status = ?, error_message = ?, updated_at = ? WHERE id = ?",
                status.name(), errorMessage, Instant.now(), id
        );
    }

    public void updateChunkCount(UUID id, int count) {
        tenantJdbc.execute(
                "UPDATE documents SET chunk_count = ?, updated_at = ? WHERE id = ?",
                count, Instant.now(), id
        );
    }

    public long countAll() {
        var results = tenantJdbc.query("SELECT COUNT(*) FROM documents WHERE status = 'COMPLETED'",
                (rs, n) -> rs.getLong(1));
        return results.isEmpty() ? 0L : results.get(0);
    }

    static class DocumentRowMapper implements RowMapper<Document> {
        @Override
        public Document mapRow(ResultSet rs, int rowNum) throws SQLException {
            return Document.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .tenantId(rs.getString("tenant_id"))
                    .filename(rs.getString("filename"))
                    .fileSizeBytes(rs.getLong("file_size_bytes"))
                    .mimeType(rs.getString("mime_type"))
                    .status(Document.ProcessingStatus.valueOf(rs.getString("status")))
                    .jobId(UUID.fromString(rs.getString("job_id")))
                    .chunkCount(rs.getObject("chunk_count", Integer.class))
                    .errorMessage(rs.getString("error_message"))
                    .createdAt(rs.getTimestamp("created_at").toInstant())
                    .updatedAt(rs.getTimestamp("updated_at") != null
                            ? rs.getTimestamp("updated_at").toInstant() : null)
                    .build();
        }
    }
}
