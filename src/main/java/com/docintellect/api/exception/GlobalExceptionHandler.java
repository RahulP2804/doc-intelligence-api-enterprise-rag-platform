package com.docintellect.api.exception;

import com.docintellect.api.model.ApiDtos;
import com.docintellect.api.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DocIntelligenceException.class)
    public ResponseEntity<ApiDtos.ErrorResponse> handleDomainException(DocIntelligenceException ex) {
        log.error("Domain exception [{}]: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(buildError(ex.getMessage(), ex.getCode()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiDtos.ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(buildError(ex.getMessage(), "BAD_REQUEST"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiDtos.ErrorResponse> handleFileTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(buildError("File exceeds maximum upload size of 50MB", "FILE_TOO_LARGE"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiDtos.ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
                .body(buildError("An internal error occurred", "INTERNAL_ERROR"));
    }

    private ApiDtos.ErrorResponse buildError(String message, String code) {
        return ApiDtos.ErrorResponse.builder()
                .error(message)
                .code(code)
                .tenantId(TenantContext.getTenantId())
                .timestamp(Instant.now())
                .build();
    }
}
