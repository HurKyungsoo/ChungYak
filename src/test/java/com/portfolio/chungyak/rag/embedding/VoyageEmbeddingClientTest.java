package com.portfolio.chungyak.rag.embedding;

import com.portfolio.chungyak.rag.embedding.EmbeddingClient.InputType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Voyage 응답 파싱 + 재시도 — 실제 API 없이 검증.
 */
class VoyageEmbeddingClientTest {

    private static final String URL = "https://api.voyageai.com/v1/embeddings";
    private final VoyageProperties props = new VoyageProperties("k", "voyage-4-lite", null, 0);

    private final VoyageEmbeddingClient client =
            new VoyageEmbeddingClient(null, props);

    @Test
    @DisplayName("data[].index 순서대로 벡터를 되돌린다 (응답이 뒤섞여 와도)")
    void reordersByIndex() {
        String json = """
            {"object":"list","data":[
              {"object":"embedding","index":1,"embedding":[0.4,0.5,0.6]},
              {"object":"embedding","index":0,"embedding":[0.1,0.2,0.3]}
            ],"model":"voyage-4-lite","usage":{"total_tokens":10}}
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
        assertThat(client.model()).isEqualTo("voyage-4-lite");
    }

    @Test
    @DisplayName("429 를 만나면 백오프 후 재시도해서 성공한다")
    void retriesOn429() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(times(2), requestTo(URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("rate limited"));
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2]}]}",
                        MediaType.APPLICATION_JSON));

        VoyageEmbeddingClient c = new VoyageEmbeddingClient(builder.build(), props, 3L);
        List<float[]> v = c.embed(List.of("안녕하세요"), InputType.DOCUMENT);

        assertThat(v.get(0)).containsExactly(0.1f, 0.2f);
        server.verify();
    }

    @Test
    @DisplayName("재시도를 다 써도 429 면 예외를 던진다")
    void givesUpAfterMaxRetries() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(times(5), requestTo(URL))   // 최초 1 + 재시도 4
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("nope"));

        VoyageEmbeddingClient c = new VoyageEmbeddingClient(builder.build(), props, 2L);

        assertThatThrownBy(() -> c.embed(List.of("x"), InputType.DOCUMENT))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.class);
        server.verify();
    }
}
