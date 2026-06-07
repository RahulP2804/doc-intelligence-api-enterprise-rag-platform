package com.docintellect.api.controller;

import com.docintellect.api.config.AppProperties;
import com.docintellect.api.ingestion.DocumentRepository;
import com.docintellect.api.model.ApiDtos;
import com.docintellect.api.tenant.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MeterRegistry meterRegistry;
    private final RedisTemplate<String, String> redisTemplate;
    private final AppProperties appProperties;
    private final DocumentRepository documentRepository;
    private final AtomicLong queueDepthGauge;

    @GetMapping
    public ResponseEntity<ApiDtos.MetricsResponse> metrics() {
        String tenantId = TenantContext.getTenantId();

        double ingestedCount = getCounterValue("docintellect.documents.ingested");
        double queriesCount = getCounterValue("docintellect.queries.served");
        double avgLatencyMs = getTimerMean("docintellect.rag.latency");

        Long queueSize = redisTemplate.opsForList().size(appProperties.getQueue().getProcessingKey());

        String embeddingModel = appProperties.getVertex().getEmbeddingModel();

        ApiDtos.MetricsResponse response = ApiDtos.MetricsResponse.builder()
                .totalDocumentsIngested((long) ingestedCount)
                .totalQueriesServed((long) queriesCount)
                .averageRagLatencyMs(avgLatencyMs)
                .embeddingModel(embeddingModel)
                .queueDepth(queueSize != null ? queueSize : 0L)
                .tenantId(tenantId)
                .reportedAt(Instant.now())
                .build();

        return ResponseEntity.ok(response);
    }

    private double getCounterValue(String name) {
        try {
            return meterRegistry.counter(name).count();
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double getTimerMean(String name) {
        try {
            Timer timer = meterRegistry.timer(name);
            return timer.count() > 0 ? timer.mean(TimeUnit.MILLISECONDS) : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
