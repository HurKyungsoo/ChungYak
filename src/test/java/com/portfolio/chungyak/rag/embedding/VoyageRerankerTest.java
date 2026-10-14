package com.portfolio.chungyak.rag.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Voyage rerank 응답 파싱 — 실제 API 없이 */
class VoyageRerankerTest {

    private final VoyageReranker reranker = new VoyageReranker(null, new RerankProperties(true, null, null, 0), "k");

    @Test
    @DisplayName("relevance_score 높은 순으로 원래 인덱스를 돌려준다, 응답에 빠진 인덱스는 뒤로")
    void ordersByScore() {
        String json = """
            {"object":"list","data":[
              {"index":2,"relevance_score":0.91},
              {"index":0,"relevance_score":0.40},
              {"index":3,"relevance_score":0.12}
            ],"model":"rerank-2.5","usage":{"total_tokens":1234}}
            """;
        assertThat(reranker.parseOrder(json, 4)).containsExactly(2, 0, 3, 1);
    }

    @Test
    @DisplayName("기본값 — rerank-2.5, 후보 20")
    void defaults() {
        assertThat(reranker.model()).isEqualTo("rerank-2.5");
        assertThat(reranker.candidates()).isEqualTo(20);
        assertThat(reranker.order("q", List.of("하나"))).containsExactly(0);   // 후보 1개면 호출 없이
    }
}
