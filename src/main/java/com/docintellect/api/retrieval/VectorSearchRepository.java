package com.docintellect.api.retrieval;

import com.docintellect.api.model.ApiDtos;
import com.docintellect.api.tenant.TenantAwareJdbcTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class VectorSearchRepository {

    private final TenantAwareJdbcTemplate tenantJdbc;

    /**
     * Retrieves the top-K most semantically similar chunks using pgvector cosine distance.
     * Query is scoped to the active tenant schema via TenantAwareJdbcTemplate.
     *
     * @param queryVector the embedded question vector
     * @param topK number of results to return
     * @param threshold minimum similarity score (cosine similarity = 1 - cosine distance)
     */
    public List<SimilarChunk> findSimilar(float[] queryVector, int topK, double threshold) {
        String vectorLiteral = toPostgresVector(queryVector);

        String sql = """
                SELECT
                    c.id AS chunk_id,
                    c.document_id,
                    c.chunk_index,
                    c.chunk_text,
                    d.filename,
                    1 - (e.embedding <=> ?::vector) AS similarity_score
                FROM embeddings e
                JOIN chunks c ON c.id = e.chunk_id
                JOIN documents d ON d.id = c.document_id
                WHERE 1 - (e.embedding <=> ?::vector) >= ?
                ORDER BY e.embedding <=> ?::vector
                LIMIT ?
                """;

        return tenantJdbc.query(sql, (rs, rowNum) -> new SimilarChunk(
                UUID.fromString(rs.getString("chunk_id")),
                UUID.fromString(rs.getString("document_id")),
                rs.getString("filename"),
                rs.getString("chunk_text"),
                rs.getDouble("similarity_score"),
                rs.getInt("chunk_index")
        ), vectorLiteral, vectorLiteral, threshold, vectorLiteral, topK);
    }

    private String toPostgresVector(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    public record SimilarChunk(
            UUID chunkId,
            UUID documentId,
            String filename,
            String chunkText,
            double similarityScore,
            int chunkIndex
    ) {}
}
