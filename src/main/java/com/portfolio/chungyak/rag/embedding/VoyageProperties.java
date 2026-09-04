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
        // 결제수단 등록(Usage tier 1) 후 16M TPM / 2000 RPM. 청크 900자짜리를 수십 개
        // 묶어도 한 요청이 3만 토큰 수준이라 여유롭다. 미등록 시엔 yml 에서 8 정도로 낮출 것.
        if (batchSize <= 0) batchSize = 64;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"REPLACE_ME".equals(apiKey);
    }
}
