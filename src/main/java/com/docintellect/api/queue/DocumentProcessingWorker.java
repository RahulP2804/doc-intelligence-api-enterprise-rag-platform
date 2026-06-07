package com.docintellect.api.queue;

import com.docintellect.api.config.AppProperties;
import com.docintellect.api.config.MetricsConfig;
import com.docintellect.api.ingestion.DocumentRepository;
import com.docintellect.api.ingestion.TextChunker;
import com.docintellect.api.model.Document;
import com.docintellect.api.service.EmbeddingService;
import com.docintellect.api.tenant.TenantAwareJdbcTemplate;
import com.docintellect.api.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentProcessingWorker {

    private final RedisTemplate<String, String> redisTemplate;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final DocumentRepository documentRepository;
    private final TenantAwareJdbcTemplate tenantJdbc;
    private final Counter documentsIngestedCounter;
    private final AtomicLong queueDepthGauge;

    @Scheduled(fixedDelay = 100)
    public void poll() {
        try {
            String queueKey = appProperties.getQueue().getProcessingKey();
            String payload = redisTemplate.opsForList()
                    .rightPop(queueKey, appProperties.getQueue().getPollTimeoutSeconds(), TimeUnit.SECONDS);

            updateQueueDepth(queueKey);

            if (payload == null || payload.isBlank()) return;

            ProcessingJobPayload job = objectMapper.readValue(payload, ProcessingJobPayload.class);
            processJob(job);

        } catch (Exception e) {
            log.error("Worker poll error: {}", e.getMessage(), e);
        }
    }

    private void processJob(ProcessingJobPayload job) {
        TenantContext.setTenantId(job.tenantId());
        TenantContext.setSchemaName(job.schemaName());

        UUID documentId = UUID.fromString(job.documentId());

        try {
            documentRepository.updateStatus(documentId, Document.ProcessingStatus.PROCESSING, null);
            log.info("Processing document {} for tenant {}", documentId, job.tenantId());

            String text = extractText(job.fileBytes());
            List<String> chunks = textChunker.chunk(text);

            persistChunksAndEmbeddings(documentId, chunks);

            documentRepository.updateStatus(documentId, Document.ProcessingStatus.COMPLETED, null);
            documentRepository.updateChunkCount(documentId, chunks.size());
            documentsIngestedCounter.increment();

            log.info("Document {} processed: {} chunks", documentId, chunks.size());

        } catch (Exception e) {
            log.error("Failed to process document {}: {}", documentId, e.getMessage(), e);
            documentRepository.updateStatus(documentId, Document.ProcessingStatus.FAILED, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    private String extractText(byte[] pdfBytes) throws Exception {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private void persistChunksAndEmbeddings(UUID documentId, List<String> chunks) {
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            UUID chunkId = UUID.randomUUID();
            int wordCount = chunkText.split("\\s+").length;

            tenantJdbc.execute(
                    "INSERT INTO chunks (id, document_id, chunk_index, chunk_text, token_count, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    chunkId, documentId, i, chunkText, wordCount, Instant.now()
            );

            EmbeddingService.EmbeddingResult result = embeddingService.embed(chunkText);
            String vectorLiteral = toPostgresVector(result.vector());

            tenantJdbc.execute(
                    "INSERT INTO embeddings (id, chunk_id, document_id, embedding, model_name, created_at) VALUES (?, ?, ?, ?::vector, ?, ?)",
                    UUID.randomUUID(), chunkId, documentId, vectorLiteral, result.modelName(), Instant.now()
            );
        }
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

    private void updateQueueDepth(String queueKey) {
        Long size = redisTemplate.opsForList().size(queueKey);
        queueDepthGauge.set(size != null ? size : 0L);
    }
}
