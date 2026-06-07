package com.docintellect.api.ingestion;

import com.docintellect.api.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits document text into overlapping windows approximating token counts.
 * Uses word-boundary splitting with a 4-chars-per-token approximation.
 */
@Component
@RequiredArgsConstructor
public class TextChunker {

    private final AppProperties appProperties;

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) return List.of();

        int chunkSizeTokens = appProperties.getRag().getChunkSizeTokens();
        int overlapTokens = appProperties.getRag().getChunkOverlapTokens();
        int chunkSizeChars = chunkSizeTokens * 4;
        int overlapChars = overlapTokens * 4;

        List<String> chunks = new ArrayList<>();
        String normalized = text.replaceAll("\\s+", " ").trim();
        int start = 0;

        while (start < normalized.length()) {
            int end = Math.min(start + chunkSizeChars, normalized.length());

            if (end < normalized.length()) {
                int wordBoundary = normalized.lastIndexOf(' ', end);
                if (wordBoundary > start) end = wordBoundary;
            }

            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) chunks.add(chunk);

            start = end - overlapChars;
            if (start <= 0 || start >= normalized.length()) break;
        }

        return chunks;
    }
}
