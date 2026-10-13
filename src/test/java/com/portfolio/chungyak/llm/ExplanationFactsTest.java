package com.portfolio.chungyak.llm;

import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExplanationFacts — 규칙 엔진 결과를 LLM 입력으로 옮기는 순수 함수. <b>최소화된 입력</b>이다 (#20).
 *
 * LLM 은 "다음에 할 일" 한두 문장만 쓰므로, 판정 분류·공고의 배정 세대·보완 힌트·필요한 입력만 넘긴다.
 * 사유 문장(사용자의 소득·자산·통장 값이 그대로 들어 있다)은 넘기지 않고, 힌트 안의 금액은 가린다.
 */
class ExplanationFactsTest {

    @Test
    @DisplayName("신청 가능 유형과 공고의 배정 세대수(공개 데이터)를 옮긴다")
    void carriesMatchesAndCounts() {
        String facts = ExplanationFacts.format(MatchResults.withMatch());

        assertThat(facts).contains("테스트아파트 1단지");
        assertThat(facts).contains("- 신청 가능: ").contains("신혼부부 47세대");
    }

    @Test
    @DisplayName("규칙이 계산한 보완 힌트를 '- [유형]' 줄로 싣는다 — 현재값·요건값 두 수치 포함")
    void carriesImprovementHints() {
        String facts = ExplanationFacts.format(MatchResults.shortfall());

        // 규제지역·통장 12개월 → 신혼부부는 "12개월 더 … 요건(24개월)"
        assertThat(facts).contains("- 보완하면 가능: 신혼부부");
        assertThat(facts).contains("- [신혼부부] 청약통장을 12개월 더").contains("(24개월)");
    }

    @Test
    @DisplayName("메울 수 없는 미충족이 섞인 유형의 힌트는 '이것만으로는 여전히 불가'로 표시한다")
    void marksHintsOfNonFixableTypes() {
        String facts = ExplanationFacts.format(MatchResults.marriageExpired());

        assertThat(facts).contains("- 지금 조건으로는 어려움: ").contains("신혼부부");
        assertThat(facts).doesNotContain("- 보완하면 가능: 신혼부부");
    }

    @Test
    @DisplayName("신청 가능 유형이 없으면 '없음'으로 적는다 — 판정을 재계산하지 않는다")
    void writesNoneWhenNoMatch() {
        assertThat(ExplanationFacts.format(MatchResults.noMatch())).contains("- 신청 가능: 없음");
    }

    @Test
    @DisplayName("개인정보 최소화 — 소득·자산·자동차가액·예치금 금액과 사유 문장이 입력에 없다")
    void noPersonalAmounts() {
        MatchResult result = MatchResults.depositShortfall();   // 월소득 432.1만·총자산 1억 2,345만·차 1,234만·예치금 123만
        String facts = ExplanationFacts.format(result);

        assertThat(ExplanationFacts.MONEY.matcher(facts).find()).as("금액 표기(…원)가 남아 있음:\n" + facts).isFalse();
        assertThat(facts).doesNotContain("%")
                .doesNotContain("432").doesNotContain("4321")
                .doesNotContain("1억").doesNotContain("2345").doesNotContain("1234").doesNotContain("123만");
        // 사유 문장은 하나도 넘기지 않는다 — 엔진이 남긴 충족·미충족 문장 어느 것도 입력에 없다
        result.decisions().values().forEach(d -> {
            d.getSatisfiedReasons().forEach(r -> assertThat(facts).doesNotContain(r));
            d.getFailedReasons().forEach(r -> assertThat(facts).doesNotContain(r));
        });
        // 예치금 힌트는 남기되 금액은 가린다 — LLM 은 "부족한 예치금"으로만 말한다
        assertThat(facts).contains("청약통장 예치금을 [금액] 더 채우면");
    }

    @Test
    @DisplayName("금액 가림 — AssetRequirement.won 형식을 모두 잡는다")
    void masksAllWonFormats() {
        assertThat(ExplanationFacts.maskMoney("예치금을 177만원 더, 기준 300만원"))
                .isEqualTo("예치금을 [금액] 더, 기준 [금액]");
        assertThat(ExplanationFacts.maskMoney("1억 2,345만원 · 1억원 · 5,000원 · 85㎡ · 12개월"))
                .isEqualTo("[금액] · [금액] · [금액] · 85㎡ · 12개월");
    }
}
