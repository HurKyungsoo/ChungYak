package com.portfolio.chungyak.llm;

import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모순 검사 — LLM 요약이 확정 판정과 어긋나는지 잡는 사후 검사.
 * 이 테스트가 검사 규칙을 고정한다.
 */
class ContradictionCheckTest {

    @Test
    @DisplayName("신청 가능한 유형이 없는데 요약이 '가능합니다' → 모순")
    void noMatchButSaysPossible() {
        MatchResult result = MatchResults.noMatch();

        assertThat(ContradictionCheck.check(result,
                "현재 조건으로는 신혼부부 특별공급을 신청 가능합니다."))
                .isPresent();
    }

    @Test
    @DisplayName("신청 가능한 유형이 없고 요약도 '없습니다' → 통과")
    void noMatchAndSaysImpossible() {
        MatchResult result = MatchResults.noMatch();

        assertThat(ContradictionCheck.check(result,
                "혼인 상태가 아니고 무주택 요건도 충족하지 못해 신청 가능한 특별공급이 없습니다."))
                .isEmpty();
    }

    @Test
    @DisplayName("신청 가능한 유형이 있는데 요약이 '없습니다' → 모순")
    void hasMatchButSaysImpossible() {
        MatchResult result = MatchResults.withMatch();

        assertThat(ContradictionCheck.check(result,
                "안타깝게도 신청 가능한 특별공급이 없습니다."))
                .isPresent();
    }

    @Test
    @DisplayName("신청 가능한 유형이 있고 요약도 긍정 → 통과")
    void hasMatchAndSaysPossible() {
        MatchResult result = MatchResults.withMatch();

        assertThat(ContradictionCheck.check(result,
                "혼인기간과 무주택·청약통장 요건을 모두 충족해 신혼부부·생애최초로 신청 가능합니다."))
                .isEmpty();
    }

    @Test
    @DisplayName("빈 요약 → 모순(재생성 대상)")
    void blankIsContradiction() {
        assertThat(ContradictionCheck.check(MatchResults.withMatch(), "   ")).isPresent();
        assertThat(ContradictionCheck.check(MatchResults.withMatch(), null)).isPresent();
    }

    @Test
    @DisplayName("'불가능', '가능성' 같은 표현만으로는 오탐하지 않는다")
    void doesNotFalsePositiveOnSubstrings() {
        MatchResult result = MatchResults.withMatch();

        // '가능성' 이 들어가지만 신청 가능 여부를 단정하진 않음
        assertThat(ContradictionCheck.check(result,
                "요건을 충족하여 신혼부부·생애최초로 신청 가능하며, 당첨 가능성은 경쟁률에 따라 달라집니다."))
                .isEmpty();
    }
}
