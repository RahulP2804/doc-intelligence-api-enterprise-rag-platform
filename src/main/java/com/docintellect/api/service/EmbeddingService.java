package com.docintellect.api.service;

import com.docintellect.api.config.AppProperties;
import com.google.cloud.aiplatform.v1.*;
import com.google.protobuf.Value;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Generates vector embeddings via GCP Vertex AI textembedding-gecko with transparent
 * OpenAI text-embedding-3-small fallback on 5xx or timeout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final AppProperties appProperties;

    public EmbeddingResult embed(String text) {
        try {
            float[] vector = embedWithVertex(text);
            return new EmbeddingResult(vector, appProperties.getVertex().getEmbeddingModel());
        } catch (Exception vertexEx) {
            log.warn("Vertex AI embedding failed, falling back to OpenAI: {}", vertexEx.getMessage());
            float[] vector = embedWithOpenAI(text);
            return new EmbeddingResult(padTo768(vector), appProperties.getOpenai().getEmbeddingModel());
        }
    }

    private float[] embedWithVertex(String text) throws Exception {
        AppProperties.Vertex cfg = appProperties.getVertex();
        String endpoint = cfg.getLocation() + "-aiplatform.googleapis.com:443";

        PredictionServiceSettings settings = PredictionServiceSettings.newBuilder()
                .setEndpoint(endpoint)
                .build();

        CompletableFuture<float[]> future = CompletableFuture.supplyAsync(() -> {
            try (PredictionServiceClient client = PredictionServiceClient.create(settings)) {
                EndpointName endpointName = EndpointName.ofProjectLocationPublisherModelName(
                        cfg.getProjectId(), cfg.getLocation(), "google", cfg.getEmbeddingModel());

                Value instance = Value.newBuilder()
                        .setStructValue(com.google.protobuf.Struct.newBuilder()
                                .putFields("content", Value.newBuilder().setStringValue(text).build()))
                        .build();

                PredictResponse response = client.predict(endpointName, List.of(instance), Value.newBuilder().build());
                return extractEmbeddingFromResponse(response);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        return future.get(cfg.getTimeoutSeconds(), TimeUnit.SECONDS);
    }

    private float[] extractEmbeddingFromResponse(PredictResponse response) {
        Value prediction = response.getPredictions(0);
        var embeddingsList = prediction.getStructValue()
                .getFieldsOrThrow("embeddings")
                .getStructValue()
                .getFieldsOrThrow("values")
                .getListValue()
                .getValuesList();

        float[] vector = new float[embeddingsList.size()];
        for (int i = 0; i < embeddingsList.size(); i++) {
            vector[i] = (float) embeddingsList.get(i).getNumberValue();
        }
        return vector;
    }

    private float[] embedWithOpenAI(String text) {
        String apiKey = appProperties.getOpenai().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("OpenAI API key not configured for fallback");
        }
        OpenAiService openAiService = new OpenAiService(apiKey);
        EmbeddingRequest request = EmbeddingRequest.builder()
                .model(appProperties.getOpenai().getEmbeddingModel())
                .input(List.of(text))
                .build();
        var response = openAiService.createEmbeddings(request);
        List<Double> values = response.getData().get(0).getEmbedding();
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = values.get(i).floatValue();
        }
        return vector;
    }

    /**
     * Pads or truncates OpenAI's 1536-dim vector to 768 to match pgvector schema.
     * In production, use a separate column or separate index per model dimension.
     */
    private float[] padTo768(float[] source) {
        float[] result = new float[768];
        System.arraycopy(source, 0, result, 0, Math.min(source.length, 768));
        return result;
    }

    public record EmbeddingResult(float[] vector, String modelName) {}
}
