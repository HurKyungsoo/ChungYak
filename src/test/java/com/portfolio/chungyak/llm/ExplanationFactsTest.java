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
    @DisplayName("규칙이 계산한 개선 안내가 '개선:' 줄로 프롬프트에 실린다 (3b)")
    void carriesImprovementHints() {
        String facts = ExplanationFacts.format(MatchResults.shortfall());

        // 규제지역·통장 12개월 → 신혼부부에 "12개월 더" 개선 안내
        assertThat(facts).contains("- 개선: ").contains("12개월 더");
        // LLM 에게 그 줄을 쓰라고 지시
        assertThat(facts).contains("'개선:'");
    }

    @Test
    @DisplayName("신청 가능 유형이 없으면 '없음'으로 적는다 — 판정을 재계산하지 않는다")
    void writesNoneWhenNoMatch() {
        String facts = ExplanationFacts.format(MatchResults.noMatch());

        assertThat(facts).contains("신청 가능한 특별공급: 없음");
    }
}
