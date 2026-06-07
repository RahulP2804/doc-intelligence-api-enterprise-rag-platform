package com.docintellect.api.model;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ApiDtos {

    @Data
    @Builder
    public static class UploadResponse {
        private UUID jobId;
        private UUID documentId;
        private String filename;
        private String status;
        private String tenantId;
        private Instant submittedAt;
    }

    @Data
    @Builder
    public static class JobStatusResponse {
        private UUID jobId;
        private String status;
        private String filename;
        private Integer chunkCount;
        private String errorMessage;
        private String tenantId;
        private Instant updatedAt;
    }

    @Data
    public static class QueryRequest {
        private String question;
        private Integer topK;
    }

    @Data
    @Builder
    public static class QueryResponse {
        private String answer;
        private List<SourceDocument> sourceDocuments;
        private String model;
        private long latencyMs;
        private String tenantId;
        private boolean grounded;
    }

    @Data
    @Builder
    public static class SourceDocument {
        private UUID documentId;
        private String filename;
        private String chunkText;
        private double similarityScore;
        private int chunkIndex;
    }

    @Data
    @Builder
    public static class TenantRegistrationRequest {
        private String tenantId;
        private String name;
        private String tier;
    }

    @Data
    @Builder
    public static class TenantRegistrationResponse {
        private String tenantId;
        private String schemaName;
        private String tier;
        private Instant createdAt;
    }

    @Data
    @Builder
    public static class MetricsResponse {
        private long totalDocumentsIngested;
        private long totalQueriesServed;
        private double averageRagLatencyMs;
        private String embeddingModel;
        private long queueDepth;
        private String tenantId;
        private Instant reportedAt;
    }

    @Data
    @Builder
    public static class ErrorResponse {
        private String error;
        private String code;
        private String tenantId;
        private Instant timestamp;
    }
}
