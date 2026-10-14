package com.portfolio.chungyak.rag.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Voyage rerank 설정 (rag.rerank.*). 키는 임베딩과 같은 {@code rag.voyage.api-key} 를 쓴다.
 * @param candidates 하이브리드 검색에서 받아 다시 매길 후보 수 (eval: 8 → 56, 12 → 59, 20 → 61 / 69)
 */
@ConfigurationProperties(prefix = "rag.rerank")
public record RerankProperties(boolean enabled, String model, String baseUrl, int candidates) {

    public RerankProperties {
        if (model == null || model.isBlank()) model = "rerank-2.5";
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.voyageai.com/v1/rerank";
        if (candidates <= 0) candidates = 20;
    }
}
