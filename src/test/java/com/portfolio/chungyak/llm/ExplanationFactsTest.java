package com.portfolio.chungyak.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExplanationFacts — 규칙 엔진 결과를 LLM 프롬프트 텍스트로 옮기는 순수 함수.
 *
 * 판정을 다시 하지 않고, 확정된 근거(수치 포함)를 빠짐없이 전달하는지 본다.
 * 개선 경로(3a)는 LLM 이 "근거에 이미 있는 수치"로만 조언하므로,
 * 그 수치가 프롬프트에 실제로 담기는지가 회귀 방지 포인트다.
 */
class ExplanationFactsTest {

    @Test
    @DisplayName("신청 가능 유형과 세대수를 근거로 옮긴다")
    void carriesMatchesAndCounts() {
        String facts = ExplanationFacts.format(MatchResults.withMatch());

        assertThat(facts).contains("테스트아파트 1단지");
        assertThat(facts).contains("신혼부부").contains("47세대");
    }

    @Test
    @DisplayName("미충족 근거의 현재값·요건값 두 수치를 그대로 전달한다")
    void carriesFailedReasonNumbers() {
        String facts = ExplanationFacts.format(MatchResults.shortfall());

        // 다자녀는 신청 가능, 신혼부부는 통장 가입기간 미달
        assertThat(facts).contains("신청 가능한 특별공급:").contains("다자녀");
        assertThat(facts).contains("[신혼부부] 자격 없음");
        assertThat(facts).contains("12개월").contains("요건(24개월)");
    }

    @Test
    @DisplayName("미충족·미확인 항목은 개선 경로까지 설명하도록 지시한다")
    void asksForImprovementPath() {
        String facts = ExplanationFacts.format(MatchResults.shortfall());

        assertThat(facts).contains("무엇을 채우면 요건을 충족하는지");
        assertThat(facts).contains("이미 있는 수치만");
    }

    @Test
    @DisplayName("신청 가능 유형이 없으면 '없음'으로 적는다 — 판정을 재계산하지 않는다")
    void writesNoneWhenNoMatch() {
        String facts = ExplanationFacts.format(MatchResults.noMatch());

        assertThat(facts).contains("신청 가능한 특별공급: 없음");
    }
}
