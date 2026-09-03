package com.portfolio.chungyak.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.portfolio.chungyak.llm.ProfileExtractionResult.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 LLM 을 호출하는 통합 테스트.
 *
 * ANTHROPIC_API_KEY 가 있을 때만 돈다(없으면 건너뜀). 매 실행마다 실제 비용이 발생한다.
 * 모델은 LLM_MODEL 로 바꿀 수 있다 (기본 claude-sonnet-5).
 *
 * 검증 대상은 프롬프트+스키마가 실제로 "명시된 값만 뽑고 애매하면 null" 을 지키는지다.
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ProfileExtractionIntegrationTest {

    private ProfileExtractionService service;

    @BeforeEach
    void setUp() {
        LlmProperties props = new LlmProperties(
                System.getenv("ANTHROPIC_API_KEY"),
                System.getenv().getOrDefault("LLM_MODEL", "claude-sonnet-5"));
        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(props.apiKey()).build();
        service = new ProfileExtractionService(
                Optional.of(new AnthropicProfileCaller(client, props)));
    }

    @Test
    @DisplayName("명확한 문장 — 값들이 정확히 추출된다")
    void extractsClearFacts() {
        ProfileExtractionResult r = service.extract(
                "결혼한 지 3년 됐고 아이가 둘이에요. 청약통장은 2년 넣었고 무주택입니다. 제가 세대주예요.");

        assertThat(r.status()).isEqualTo(Status.EXTRACTED);
        ExtractedProfile p = r.profile();
        assertThat(p.married()).isTrue();
        assertThat(p.childCount()).isEqualTo(2);
        assertThat(p.monthsSinceMarriage()).isBetween(30, 42);
        assertThat(p.accountMonths()).isEqualTo(24);
        assertThat(p.houseless()).isTrue();
        assertThat(p.householdHead()).isTrue();
    }

    @Test
    @DisplayName("애매한 문장 — 혼인 여부는 잡되 혼인 기간은 추측하지 않고 null 로 둔다")
    void leavesAmbiguousFieldNull() {
        ProfileExtractionResult r = service.extract(
                "신혼인 것 같은데 정확히 언제 결혼했는지는 기억이 잘 안 나요.");

        assertThat(r.status()).isEqualTo(Status.EXTRACTED);
        assertThat(r.profile().monthsSinceMarriage()).isNull();
        assertThat(r.unknownFieldLabels()).contains("혼인 기간(개월)");
    }

    @Test
    @DisplayName("조건과 무관한 문장 — 아무 값도 못 뽑아 FAILED")
    void unrelatedTextFails() {
        assertThat(service.extract("오늘 점심 뭐 먹지").status()).isEqualTo(Status.FAILED);
    }
}
