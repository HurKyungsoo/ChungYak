package com.portfolio.chungyak.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.portfolio.chungyak.llm.ExplanationResult.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 LLM 로 판정 요약을 생성하는 통합 테스트.
 *
 * ANTHROPIC_API_KEY 가 있을 때만 돈다. 검증 대상:
 *  - 요약이 판정과 모순되지 않는다 (모순 검사 통과 → Status.AI)
 *  - 판정 결과(가능/불가능)를 뒤집지 않는다
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ExplanationIntegrationTest {

    private ExplanationService service;

    @BeforeEach
    void setUp() {
        LlmProperties props = new LlmProperties(
                System.getenv("ANTHROPIC_API_KEY"),
                System.getenv().getOrDefault("LLM_MODEL", "claude-sonnet-5"));
        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(props.apiKey()).build();
        service = new ExplanationService(Optional.of(new AnthropicExplainer(client, props)));
    }

    @Test
    @DisplayName("신청 가능한 경우 — 요약이 판정과 일치하고 모순 검사를 통과한다")
    void summarizesEligibleWithoutContradiction() {
        ExplanationResult result = service.explain(MatchResults.withMatch());

        assertThat(result.status()).isEqualTo(Status.AI);
        assertThat(result.text()).isNotBlank();
        assertThat(ContradictionCheck.check(MatchResults.withMatch(), result.text())).isEmpty();
    }

    @Test
    @DisplayName("신청 불가한 경우 — 요약이 '가능하다'로 뒤집지 않는다")
    void doesNotFlipIneligibleToEligible() {
        ExplanationResult result = service.explain(MatchResults.noMatch());

        // AI 든 폴백이든, 결과 문장은 모순이 없어야 한다
        assertThat(result.isShown()).isTrue();
        assertThat(ContradictionCheck.check(MatchResults.noMatch(), result.text())).isEmpty();
    }
}
