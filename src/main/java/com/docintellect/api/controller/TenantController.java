package com.docintellect.api.controller;

import com.docintellect.api.model.ApiDtos;
import com.docintellect.api.tenant.TenantProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantProvisioningService provisioningService;

    /**
     * Registers a new tenant: creates an isolated PostgreSQL schema and initializes
     * documents, chunks, and embeddings tables with pgvector index.
     */
    @PostMapping
    public ResponseEntity<ApiDtos.TenantRegistrationResponse> register(
            @RequestBody ApiDtos.TenantRegistrationRequest request) {
        ApiDtos.TenantRegistrationResponse response = provisioningService.provisionTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
