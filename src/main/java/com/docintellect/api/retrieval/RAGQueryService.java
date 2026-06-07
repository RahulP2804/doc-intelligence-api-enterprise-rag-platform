package com.docintellect.api.retrieval;

import com.docintellect.api.config.AppProperties;
import com.docintellect.api.model.ApiDtos;
import com.docintellect.api.service.EmbeddingService;
import com.docintellect.api.service.GenerationService;
import com.docintellect.api.tenant.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RAGQueryService {

    private static final String INSUFFICIENT_CONTEXT_MARKER = "INSUFFICIENT_CONTEXT";

    private final EmbeddingService embeddingService;
    private final VectorSearchRepository vectorSearch;
    private final GenerationService generationService;
    private final AppProperties appProperties;
    private final Counter queriesServedCounter;
    private final Timer ragLatencyTimer;

    public ApiDtos.QueryResponse query(ApiDtos.QueryRequest request) {
        String tenantId = TenantContext.getTenantId();
        int topK = request.getTopK() != null ? request.getTopK() : appProperties.getRag().getDefaultTopK();
        double threshold = appProperties.getRag().getSimilarityThreshold();

        long[] latencyHolder = new long[1];

        ApiDtos.QueryResponse response = ragLatencyTimer.record(() -> {
            long start = System.currentTimeMillis();

            EmbeddingService.EmbeddingResult queryEmbedding = embeddingService.embed(request.getQuestion());

            List<VectorSearchRepository.SimilarChunk> chunks = vectorSearch.findSimilar(
                    queryEmbedding.vector(), topK, threshold
            );

            if (chunks.isEmpty()) {
                latencyHolder[0] = System.currentTimeMillis() - start;
                return buildInsufficientContextResponse(tenantId, queryEmbedding.modelName(), latencyHolder[0]);
            }

            String context = buildContext(chunks);
            GenerationService.GenerationResult generated = generationService.generate(request.getQuestion(), context);

            if (generated.answer().contains(INSUFFICIENT_CONTEXT_MARKER)) {
                latencyHolder[0] = System.currentTimeMillis() - start;
                return buildInsufficientContextResponse(tenantId, generated.modelName(), latencyHolder[0]);
            }

            List<ApiDtos.SourceDocument> sources = chunks.stream()
                    .map(c -> ApiDtos.SourceDocument.builder()
                            .documentId(c.documentId())
                            .filename(c.filename())
                            .chunkText(c.chunkText().substring(0, Math.min(200, c.chunkText().length())) + "...")
                            .similarityScore(c.similarityScore())
                            .chunkIndex(c.chunkIndex())
                            .build())
                    .collect(Collectors.toList());

            latencyHolder[0] = System.currentTimeMillis() - start;

            return ApiDtos.QueryResponse.builder()
                    .answer(generated.answer())
                    .sourceDocuments(sources)
                    .model(generated.modelName())
                    .latencyMs(latencyHolder[0])
                    .tenantId(tenantId)
                    .grounded(true)
                    .build();
        });

        queriesServedCounter.increment();
        log.info("Query served for tenant {} in {}ms via {}", tenantId, response.getLatencyMs(), response.getModel());
        return response;
    }

    private String buildContext(List<VectorSearchRepository.SimilarChunk> chunks) {
        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            VectorSearchRepository.SimilarChunk chunk = chunks.get(i);
            ctx.append("--- Source [").append(i + 1).append("]: ").append(chunk.filename())
               .append(" (similarity: ").append(String.format("%.3f", chunk.similarityScore())).append(") ---\n")
               .append(chunk.chunkText()).append("\n\n");
        }
        return ctx.toString();
    }

    private ApiDtos.QueryResponse buildInsufficientContextResponse(String tenantId, String model, long latencyMs) {
        return ApiDtos.QueryResponse.builder()
                .answer("The uploaded documents do not contain sufficient information to answer this question.")
                .sourceDocuments(List.of())
                .model(model)
                .latencyMs(latencyMs)
                .tenantId(tenantId)
                .grounded(false)
                .build();
    }
}
