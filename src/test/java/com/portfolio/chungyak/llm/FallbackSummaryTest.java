package com.portfolio.chungyak.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 폴백 요약 — LLM 을 못 쓸 때 규칙 데이터로만 조립하는 한 문단.
 * 정의상 판정과 어긋날 수 없으므로, 모순 검사도 통과해야 한다.
 */
class FallbackSummaryTest {

    @Test
    @DisplayName("신청 가능 유형이 있으면 유형·세대수를 나열한다")
    void listsApplicableTypes() {
        String text = FallbackSummary.of(MatchResults.withMatch());

        assertThat(text)
                .contains("신혼부부 47세대")
                .contains("생애최초 22세대")
                .contains("신청 가능한 특별공급");
        assertThat(ContradictionCheck.check(MatchResults.withMatch(), text)).isEmpty();
    }

    @Test
    @DisplayName("신청 가능 유형이 없으면 '없습니다' 로 끝낸다")
    void saysNoneWhenNoMatch() {
        String text = FallbackSummary.of(MatchResults.noMatch());

        assertThat(text).contains("신청 가능한 특별공급이 없습니다");
        assertThat(ContradictionCheck.check(MatchResults.noMatch(), text)).isEmpty();
    }

    @Test
    @DisplayName("자격은 되지만 물량 없는 유형은 따로 언급한다")
    void mentionsQualifiedButUnavailable() {
        String text = FallbackSummary.of(MatchResults.qualifiedButUnavailable());

        assertThat(text)
                .contains("신혼부부 47세대")
                .contains("생애최초").contains("다자녀가구")
                .contains("자격은 되지만");
        assertThat(ContradictionCheck.check(MatchResults.qualifiedButUnavailable(), text)).isEmpty();
    }
}
