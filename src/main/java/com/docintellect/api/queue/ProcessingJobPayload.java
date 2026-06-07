package com.docintellect.api.queue;

public record ProcessingJobPayload(
        String jobId,
        String documentId,
        String tenantId,
        String schemaName,
        byte[] fileBytes
) {}
