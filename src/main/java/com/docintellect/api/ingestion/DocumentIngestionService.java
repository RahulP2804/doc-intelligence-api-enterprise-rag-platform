package com.docintellect.api.ingestion;

import com.docintellect.api.config.AppProperties;
import com.docintellect.api.model.ApiDtos;
import com.docintellect.api.model.Document;
import com.docintellect.api.queue.ProcessingJobPayload;
import com.docintellect.api.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final DocumentRepository documentRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public ApiDtos.UploadResponse ingest(MultipartFile file) throws IOException {
        validateFile(file);

        String tenantId = TenantContext.getTenantId();
        UUID documentId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        Document document = Document.builder()
                .id(documentId)
                .tenantId(tenantId)
                .filename(file.getOriginalFilename())
                .fileSizeBytes(file.getSize())
                .mimeType(file.getContentType())
                .status(Document.ProcessingStatus.QUEUED)
                .jobId(jobId)
                .createdAt(Instant.now())
                .build();

        documentRepository.save(document);

        ProcessingJobPayload payload = new ProcessingJobPayload(
                jobId.toString(),
                documentId.toString(),
                tenantId,
                TenantContext.getSchemaName(),
                file.getBytes()
        );

        redisTemplate.opsForList().leftPush(
                appProperties.getQueue().getProcessingKey(),
                objectMapper.writeValueAsString(payload)
        );

        log.info("Document queued: documentId={} jobId={} tenant={}", documentId, jobId, tenantId);

        return ApiDtos.UploadResponse.builder()
                .jobId(jobId)
                .documentId(documentId)
                .filename(file.getOriginalFilename())
                .status(Document.ProcessingStatus.QUEUED.name())
                .tenantId(tenantId)
                .submittedAt(Instant.now())
                .build();
    }

    public ApiDtos.JobStatusResponse getJobStatus(UUID jobId) {
        Document doc = documentRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        return ApiDtos.JobStatusResponse.builder()
                .jobId(jobId)
                .status(doc.getStatus().name())
                .filename(doc.getFilename())
                .chunkCount(doc.getChunkCount())
                .errorMessage(doc.getErrorMessage())
                .tenantId(doc.getTenantId())
                .updatedAt(doc.getUpdatedAt() != null ? doc.getUpdatedAt() : doc.getCreatedAt())
                .build();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty or missing");
        }
        String contentType = file.getContentType();
        if (!"application/pdf".equals(contentType)) {
            throw new IllegalArgumentException("Only PDF files are supported. Received: " + contentType);
        }
    }
}
