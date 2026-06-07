package com.docintellect.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Vertex vertex = new Vertex();
    private OpenAi openai = new OpenAi();
    private Rag rag = new Rag();
    private RateLimit rateLimit = new RateLimit();
    private Queue queue = new Queue();

    @Data
    public static class Vertex {
        private String projectId;
        private String location;
        private String credentialsPath;
        private String embeddingModel;
        private String generationModel;
        private int timeoutSeconds = 10;
    }

    @Data
    public static class OpenAi {
        private String apiKey;
        private String embeddingModel;
        private String generationModel;
    }

    @Data
    public static class Rag {
        private double similarityThreshold = 0.75;
        private int defaultTopK = 5;
        private int chunkSizeTokens = 512;
        private int chunkOverlapTokens = 64;
    }

    @Data
    public static class RateLimit {
        private Map<String, Integer> tiers;
        private int windowSeconds = 60;
    }

    @Data
    public static class Queue {
        private String processingKey;
        private int pollTimeoutSeconds = 5;
    }
}
