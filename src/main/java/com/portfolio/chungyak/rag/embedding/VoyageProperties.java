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
        if (model == null || model.isBlank()) model = "voyage-4-lite";
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.voyageai.com/v1/embeddings";
        // 한 요청의 토큰 수를 낮게 유지한다. 결제수단 미등록 계정은 10K TPM 제한이라
        // 청크를 많이 묶으면(청크 900자 × N) 한 요청만으로도 분당 한도를 넘겨 계속 429 가 난다.
        if (batchSize <= 0) batchSize = 8;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"REPLACE_ME".equals(apiKey);
    }
}
