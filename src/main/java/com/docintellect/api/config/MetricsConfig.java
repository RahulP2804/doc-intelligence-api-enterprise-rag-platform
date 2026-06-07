package com.docintellect.api.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class MetricsConfig {

    @Getter
    private final AtomicLong queueDepth = new AtomicLong(0);

    @Bean
    public Counter documentsIngestedCounter(MeterRegistry registry) {
        return Counter.builder("docintellect.documents.ingested")
                .description("Total documents successfully ingested")
                .register(registry);
    }

    @Bean
    public Counter queriesServedCounter(MeterRegistry registry) {
        return Counter.builder("docintellect.queries.served")
                .description("Total RAG queries served")
                .register(registry);
    }

    @Bean
    public Counter vertexAiCallsCounter(MeterRegistry registry) {
        return Counter.builder("docintellect.llm.calls")
                .tag("provider", "vertex")
                .description("Vertex AI API calls")
                .register(registry);
    }

    @Bean
    public Counter openAiFallbackCounter(MeterRegistry registry) {
        return Counter.builder("docintellect.llm.calls")
                .tag("provider", "openai_fallback")
                .description("OpenAI fallback API calls")
                .register(registry);
    }

    @Bean
    public Timer ragLatencyTimer(MeterRegistry registry) {
        return Timer.builder("docintellect.rag.latency")
                .description("End-to-end RAG query latency")
                .publishPercentiles(0.50, 0.90, 0.99)
                .register(registry);
    }

    @Bean
    public AtomicLong queueDepthGauge(MeterRegistry registry) {
        registry.gauge("docintellect.queue.depth", queueDepth);
        return queueDepth;
    }
}
