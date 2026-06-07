package com.docintellect.api.controller;

import com.docintellect.api.model.ApiDtos;
import com.docintellect.api.retrieval.RAGQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class QueryController {

    private final RAGQueryService ragQueryService;

    /**
     * Accepts a natural language question, retrieves grounded context from tenant documents,
     * and returns an LLM-generated answer with source attribution.
     */
    @PostMapping("/query")
    public ResponseEntity<ApiDtos.QueryResponse> query(@RequestBody @Valid ApiDtos.QueryRequest request) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new IllegalArgumentException("Question must not be blank");
        }
        ApiDtos.QueryResponse response = ragQueryService.query(request);
        return ResponseEntity.ok(response);
    }
}
