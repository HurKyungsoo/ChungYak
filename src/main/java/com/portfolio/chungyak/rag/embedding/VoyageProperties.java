package com.portfolio.chungyak.rag.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Voyage AI 임베딩 설정 (rag.voyage.*).
 *
 * api-key 가 비어 있거나 REPLACE_ME 면 {@link VoyageEmbeddingClient} 빈을 만들지 않는다.
 */
@ConfigurationProperties(prefix = "rag.voyage")
public record VoyageProperties(String apiKey, String model, String baseUrl, int batchSize) {

    public VoyageProperties {
        if (model == null || model.isBlank()) model = "voyage-3-lite";
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.voyageai.com/v1/embeddings";
        if (batchSize <= 0) batchSize = 96;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"REPLACE_ME".equals(apiKey);
    }
}
