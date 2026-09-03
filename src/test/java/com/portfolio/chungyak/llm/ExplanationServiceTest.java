package com.portfolio.chungyak.llm;

import com.portfolio.chungyak.llm.ExplanationResult.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExplanationService — 실제 LLM 없이 검증.
 *
 * 핵심: 모순되는 요약은 재시도 → 그래도 모순이면 결정론적 폴백으로 대체.
 * (LlmExplainer 를 목으로 고정)
 */
class ExplanationServiceTest {

    private static ExplanationService withExplainer(LlmExplainer explainer) {
        return new ExplanationService(Optional.of(explainer));
    }

    @Test
    @DisplayName("API 키 없음 → DISABLED, LLM 호출 안 함")
    void disabledWithoutExplainer() {
        ExplanationService service = new ExplanationService(Optional.empty());

        ExplanationResult result = service.explain(MatchResults.withMatch());

        assertThat(service.isAvailable()).isFalse();
        assertThat(result.status()).isEqualTo(Status.DISABLED);
        assertThat(result.isShown()).isFalse();
    }

    @Test
    @DisplayName("모순 없는 요약 → 그대로 AI 요약으로 채택")
    void cleanSummaryIsAccepted() {
        ExplanationService service = withExplainer(facts ->
                "혼인기간과 무주택·청약통장 요건을 충족해 신혼부부·생애최초로 신청 가능합니다.");

        ExplanationResult result = service.explain(MatchResults.withMatch());

        assertThat(result.status()).isEqualTo(Status.AI);
        assertThat(result.isAiGenerated()).isTrue();
    }

    @Test
    @DisplayName("판정과 모순되는 요약이 계속 나오면 → 결정론적 폴백")
    void contradictorySummaryFallsBack() {
        // 실제로는 신청 가능 유형이 없는데 매번 "가능합니다" 라고 우기는 LLM
        ExplanationService service = withExplainer(facts ->
                "축하합니다! 신혼부부 특별공급을 신청 가능합니다.");

        ExplanationResult result = service.explain(MatchResults.noMatch());

        assertThat(result.status()).isEqualTo(Status.FALLBACK);
        assertThat(result.isAiGenerated()).isFalse();
        assertThat(result.text()).contains("신청 가능한 특별공급이 없습니다");
        // 폴백은 정의상 모순이 없어야 한다
        assertThat(ContradictionCheck.check(MatchResults.noMatch(), result.text())).isEmpty();
    }

    @Test
    @DisplayName("첫 응답만 모순이고 재생성이 멀쩡하면 → AI 요약 채택")
    void retrySucceeds() {
        AtomicInteger calls = new AtomicInteger();
        ExplanationService service = withExplainer(facts -> calls.incrementAndGet() == 1
                ? "신혼부부 특별공급을 신청 가능합니다."               // 모순 (noMatch 인데)
                : "혼인 상태가 아니어서 신청 가능한 특별공급이 없습니다.");  // OK

        ExplanationResult result = service.explain(MatchResults.noMatch());

        assertThat(calls.get()).isEqualTo(2);
        assertThat(result.status()).isEqualTo(Status.AI);
    }

    @Test
    @DisplayName("LLM 이 예외를 던지면 → 폴백")
    void llmExceptionFallsBack() {
        ExplanationService service = withExplainer(facts -> {
            throw new RuntimeException("503 overloaded");
        });

        ExplanationResult result = service.explain(MatchResults.withMatch());

        assertThat(result.status()).isEqualTo(Status.FALLBACK);
        assertThat(result.text()).contains("신혼부부 47세대");
    }

    @Test
    @DisplayName("빈 응답이 반복되면 → 폴백")
    void blankResponseFallsBack() {
        ExplanationService service = withExplainer(facts -> "   ");

        assertThat(service.explain(MatchResults.withMatch()).status()).isEqualTo(Status.FALLBACK);
    }
}
