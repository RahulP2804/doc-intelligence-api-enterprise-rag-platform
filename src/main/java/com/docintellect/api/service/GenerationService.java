package com.docintellect.api.service;

import com.docintellect.api.config.AppProperties;
import com.google.cloud.aiplatform.v1beta1.*;
import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Generates grounded natural language answers via Vertex AI Gemini 1.5 Flash,
 * with silent fallback to OpenAI GPT-4o-mini on 5xx or timeout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationService {

    private static final String SYSTEM_PROMPT = """
            You are a document intelligence assistant. Answer the user's question using ONLY
            the provided document context. If the context does not contain sufficient information
            to answer confidently, respond with: "INSUFFICIENT_CONTEXT".
            Be concise, factual, and cite which document sections support your answer.
            """;

    private final AppProperties appProperties;

    public GenerationResult generate(String question, String context) {
        String prompt = buildPrompt(question, context);
        try {
            String answer = generateWithVertex(prompt);
            return new GenerationResult(answer, appProperties.getVertex().getGenerationModel());
        } catch (Exception vertexEx) {
            log.warn("Vertex AI generation failed, falling back to OpenAI: {}", vertexEx.getMessage());
            String answer = generateWithOpenAI(prompt);
            return new GenerationResult(answer, appProperties.getOpenai().getGenerationModel());
        }
    }

    private String generateWithVertex(String prompt) throws Exception {
        AppProperties.Vertex cfg = appProperties.getVertex();
        String endpoint = cfg.getLocation() + "-aiplatform.googleapis.com:443";

        PredictionServiceSettings settings = com.google.cloud.aiplatform.v1beta1.PredictionServiceSettings
                .newBuilder()
                .setEndpoint(endpoint)
                .build();

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try (com.google.cloud.aiplatform.v1beta1.PredictionServiceClient client =
                         com.google.cloud.aiplatform.v1beta1.PredictionServiceClient.create(settings)) {

                EndpointName endpointName = EndpointName.ofProjectLocationPublisherModelName(
                        cfg.getProjectId(), cfg.getLocation(), "google", cfg.getGenerationModel());

                Content userContent = Content.newBuilder()
                        .setRole("user")
                        .addParts(Part.newBuilder().setText(prompt).build())
                        .build();

                GenerateContentRequest request = GenerateContentRequest.newBuilder()
                        .setModel(endpointName.toString())
                        .addContents(userContent)
                        .build();

                GenerateContentResponse response = client.generateContent(request);
                return response.getCandidates(0).getContent().getParts(0).getText();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        return future.get(cfg.getTimeoutSeconds(), TimeUnit.SECONDS);
    }

    private String generateWithOpenAI(String prompt) {
        String apiKey = appProperties.getOpenai().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("OpenAI API key not configured for fallback");
        }
        OpenAiService service = new OpenAiService(apiKey);
        ChatMessage systemMsg = new ChatMessage(ChatMessageRole.SYSTEM.value(), SYSTEM_PROMPT);
        ChatMessage userMsg = new ChatMessage(ChatMessageRole.USER.value(), prompt);

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(appProperties.getOpenai().getGenerationModel())
                .messages(List.of(systemMsg, userMsg))
                .maxTokens(1024)
                .temperature(0.1)
                .build();

        return service.createChatCompletion(request).getChoices().get(0).getMessage().getContent();
    }

    private String buildPrompt(String question, String context) {
        return """
                DOCUMENT CONTEXT:
                %s
                
                USER QUESTION:
                %s
                
                Provide a precise, grounded answer based solely on the context above.
                """.formatted(context, question);
    }

    public record GenerationResult(String answer, String modelName) {}
}
