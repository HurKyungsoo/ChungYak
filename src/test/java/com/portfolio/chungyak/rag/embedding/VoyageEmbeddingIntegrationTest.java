package com.portfolio.chungyak.rag.embedding;

import com.portfolio.chungyak.rag.embedding.EmbeddingClient.InputType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Voyage AI 를 호출하는 통합 테스트.
 *
 * VOYAGE_API_KEY 가 있을 때만 돈다. 매 실행마다 비용이 발생한다(문장 3개 임베딩 ≈ 극소).
 * 검증: 차원이 일정하고, 의미가 가까운 문장 쌍이 먼 쌍보다 코사인이 높다.
 */
@EnabledIfEnvironmentVariable(named = "VOYAGE_API_KEY", matches = ".+")
class VoyageEmbeddingIntegrationTest {

    private EmbeddingClient client;

    @BeforeEach
    void setUp() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(5));
        f.setReadTimeout(Duration.ofSeconds(30));
        RestClient rc = RestClient.builder().requestFactory(f).build();
        client = new VoyageEmbeddingClient(rc, new VoyageProperties(
                System.getenv("VOYAGE_API_KEY"),
                System.getenv().getOrDefault("VOYAGE_MODEL", "voyage-4-lite"), null, 0));
    }

    @Test
    @DisplayName("한국어 문장 임베딩 — 차원 일정 + 의미 유사도가 반영된다")
    void embedsKoreanWithSemanticSimilarity() {
        List<float[]> v = client.embed(List.of(
                "잔여세대 신청 자격은 무주택세대구성원입니다.",
                "남은 물량은 집이 없는 세대만 청약할 수 있습니다.",
                "오늘 점심 메뉴로 김치찌개를 먹었다."), InputType.DOCUMENT);

        assertThat(v).hasSize(3);
        assertThat(v.get(0)).hasSameSizeAs(v.get(1)).hasSizeGreaterThan(100);

        double near = cos(v.get(0), v.get(1));
        double far = cos(v.get(0), v.get(2));
        assertThat(near).isGreaterThan(far);
    }

    private static double cos(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
