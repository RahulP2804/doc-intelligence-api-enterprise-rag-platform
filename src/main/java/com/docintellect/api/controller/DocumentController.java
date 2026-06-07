package com.docintellect.api.controller;

import com.docintellect.api.ingestion.DocumentIngestionService;
import com.docintellect.api.model.ApiDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentIngestionService ingestionService;

    /**
     * Accepts a PDF upload, persists metadata, and enqueues for async processing.
     * Returns 202 Accepted with a jobId for polling.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiDtos.UploadResponse> upload(@RequestParam("file") MultipartFile file)
            throws IOException {
        ApiDtos.UploadResponse response = ingestionService.ingest(file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Polls the processing status of an ingestion job.
     */
    @GetMapping("/{jobId}/status")
    public ResponseEntity<ApiDtos.JobStatusResponse> status(@PathVariable UUID jobId) {
        ApiDtos.JobStatusResponse response = ingestionService.getJobStatus(jobId);
        return ResponseEntity.ok(response);
    }
}
