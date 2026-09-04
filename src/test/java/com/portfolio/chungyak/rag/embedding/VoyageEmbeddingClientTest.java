package com.portfolio.chungyak.rag.embedding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Voyage 응답 파싱 — 실제 API 없이 픽스처로 검증.
 * data[].index 로 재정렬하는지, 크기 불일치를 잡는지.
 */
class VoyageEmbeddingClientTest {

    private final VoyageEmbeddingClient client =
            new VoyageEmbeddingClient(null, new VoyageProperties("k", "voyage-3-lite", null, 0));

    @Test
    @DisplayName("data[].index 순서대로 벡터를 되돌린다 (응답이 뒤섞여 와도)")
    void reordersByIndex() {
        String json = """
            {"object":"list","data":[
              {"object":"embedding","index":1,"embedding":[0.4,0.5,0.6]},
              {"object":"embedding","index":0,"embedding":[0.1,0.2,0.3]}
            ],"model":"voyage-3-lite","usage":{"total_tokens":10}}
            """;

        List<float[]> vectors = client.parseEmbeddings(json, 2);

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(vectors.get(1)).containsExactly(0.4f, 0.5f, 0.6f);
    }

    @Test
    @DisplayName("data 크기가 기대와 다르면 예외")
    void sizeMismatchThrows() {
        String json = """
            {"data":[{"index":0,"embedding":[0.1]}]}
            """;
        assertThatThrownBy(() -> client.parseEmbeddings(json, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("크기 불일치");
    }

    @Test
    @DisplayName("model() 은 설정값")
    void modelFromProps() {
        assertThat(client.model()).isEqualTo("voyage-3-lite");
    }
}
