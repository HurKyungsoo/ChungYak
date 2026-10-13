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
    @DisplayName("유형별 '자격 없음'은 사실이면 정상 — 공고 전체 불가 단정으로 오탐하지 않는다 (Groq eval 에서 3/5 가 이 오탐이었다)")
    void perTypeNegativeIsNotOverallNegative() {
        MatchResult result = MatchResults.withMatch();   // 신혼부부·생애최초 가능, 다자녀 자격 없음

        assertThat(ContradictionCheck.check(result,
                "신혼부부와 생애최초는 신청 가능합니다. 다자녀가구는 미성년 자녀 수 요건을 충족하지 못해 자격이 없습니다."))
                .isEmpty();
        assertThat(ContradictionCheck.check(result,
                "**신혼부부** – 신청 가능\n**다자녀가구** – 자격이 없습니다. 자녀가 부족해 신청할 수 없습니다."))
                .isEmpty();
        // "따라서 신청할 수 없습니다" 는 바로 앞에 말한 유형(다자녀가구)에 대한 말이다
        assertThat(ContradictionCheck.check(result,
                "다자녀가구는 미성년 자녀가 부족합니다. 따라서 신청할 수 없습니다."))
                .isEmpty();
    }

    @Test
    @DisplayName("유형별 판정을 뒤집으면 모순 — 신청 가능한 유형을 '자격 없음'으로, 자격 없는 유형을 '가능'으로")
    void flipsPerTypeVerdict() {
        MatchResult result = MatchResults.withMatch();

        assertThat(ContradictionCheck.check(result, "신혼부부는 자격이 없습니다.")).get().asString().contains("신혼부부");
        assertThat(ContradictionCheck.check(result, "신혼부부는 신청 가능합니다. 생애최초는 신청할 수 없습니다.")).isPresent();
        assertThat(ContradictionCheck.check(result, "다자녀가구도 신청 가능합니다.")).get().asString().contains("다자녀가구");

        // 혼인 7년 초과 — 신혼부부는 탈락, 생애최초는 가능
        MatchResult expired = MatchResults.marriageExpired();
        assertThat(ContradictionCheck.check(expired,
                "생애최초는 신청 가능합니다. 신혼부부는 혼인 기간 초과로 자격이 없습니다.")).isEmpty();
        assertThat(ContradictionCheck.check(expired,
                "생애최초는 혼인 기간과 무관하게 자격이 없습니다.")).isPresent();
    }

    @Test
    @DisplayName("자격은 되지만 물량이 없는 유형을 '신청할 수 없다'고 쓰는 건 사실이다")
    void qualifiedButUnavailableCannotApply() {
        MatchResult result = MatchResults.qualifiedButUnavailable();

        assertThat(ContradictionCheck.check(result,
                "다자녀가구는 자격이 되지만 이 공고에 물량이 없어 신청할 수 없습니다.")).isEmpty();
    }

    @Test
    @DisplayName("근거에 없는 숫자는 지어낸 값 — 세대수·개월·년을 만들어내면 모순")
    void inventedNumbers() {
        MatchResult result = MatchResults.withMatch();   // 신혼부부 47, 생애최초 22

        assertThat(ContradictionCheck.check(result, "신혼부부 99세대를 신청할 수 있습니다.")).get().asString().contains("99");
        assertThat(ContradictionCheck.check(result, "신혼부부 47세대, 생애최초 22세대를 신청할 수 있습니다.")).isEmpty();
        assertThat(ContradictionCheck.check(result, "두 유형을 합쳐 69세대입니다.")).isEmpty();   // 47+22 합계
        assertThat(ContradictionCheck.check(result, "1. 신혼부부\n2. 생애최초 모두 신청 가능합니다.")).isEmpty();   // 줄 머리 목록 번호
    }

    @Test
    @DisplayName("조건을 설명하는 말은 자격 단정이 아니다 — '세대주만 신청할 수 있는 제한', '…신청할 수 있어 자격이 부족합니다' (Groq eval 오탐 3건)")
    void conditionDescriptionsAreNotClaims() {
        MatchResult result = MatchResults.withMatch();   // 신혼부부·생애최초 가능, 다자녀·노부모부양 등은 자격 없음

        // 앞 절의 유형(다자녀가구)이 다음 절(노부모부양)의 "신청할 수 있다는 제한" 에 따라붙어 오탐하던 문장
        assertThat(ContradictionCheck.check(result,
                "다자녀가구는 미성년 자녀가 기준보다 부족하고, 노부모부양은 고령의 직계존속을 "
                        + "부양하지 않아 요건을 충족하지 못하며 세대주만 신청할 수 있다는 추가 제한도 있습니다.")).isEmpty();
        assertThat(ContradictionCheck.check(result,
                "**노부모부양** – 신청이 불가능합니다. 직계존속을 부양하지 않았으며, 세대주만 신청할 수 있는 제한도 만족하지 못합니다.")).isEmpty();
        assertThat(ContradictionCheck.check(result,
                "노부모부양은 부양 요건이 안 맞고, 세대주만 신청할 수 있어 자격이 부족합니다.")).isEmpty();
        // 그래도 진짜 긍정 단정은 잡는다 — "신청할 수 있는 특별공급" 은 단정이다
        assertThat(ContradictionCheck.check(result, "노부모부양은 신청할 수 있는 특별공급입니다.")).isPresent();
        assertThat(ContradictionCheck.check(result, "노부모부양도 신청 가능합니다.")).isPresent();
    }

    @Test
    @DisplayName("'신청 가능 유형: 없음' 같은 라벨형 부정은 부정이다 — '신청 가능'에 걸려 오탐하지 않는다. 0 은 지어낸 값이 아니다")
    void labelStyleNegativeAndZero() {
        MatchResult none = MatchResults.noMatch();

        assertThat(ContradictionCheck.check(none, "**테스트아파트 1단지** 특별공급 신청 가능 유형: 없음")).isEmpty();
        assertThat(ContradictionCheck.check(none, "신청 가능한 특별공급은 없습니다.")).isEmpty();
        // 같은 문구라도 신청 가능한 유형이 있으면 모순
        assertThat(ContradictionCheck.check(MatchResults.withMatch(), "특별공급 신청 가능 유형: 없음")).isPresent();
        // 물량이 0세대라는 말
        assertThat(ContradictionCheck.check(MatchResults.qualifiedButUnavailable(),
                "다자녀가구는 자격이 되지만 배정이 0세대라 신청할 수 없습니다.")).isEmpty();
    }

    @Test
    @DisplayName("근거에서 계산되는 숫자는 허용 — 모자란 개월 수(차이), 개월→년")
    void derivedNumbersAllowed() {
        MatchResult result = MatchResults.shortfall();   // 신혼부부: 통장 12개월 / 요건 24개월

        assertThat(ContradictionCheck.check(result,
                "다자녀가구는 신청 가능합니다. 신혼부부는 통장이 12개월이라 요건 24개월(2년)에 12개월(1년) 모자랍니다.")).isEmpty();
        assertThat(ContradictionCheck.check(result,
                "신혼부부는 통장이 5년 더 필요합니다.")).isPresent();
    }

    @Test
    @DisplayName("보완 수치를 다른 유형에 붙이면 모순 — 숫자가 근거에 있어도 엉뚱한 유형에 대한 조언이다 (#20)")
    void hintNumberOnWrongType() {
        MatchResult result = MatchResults.shortfall();   // 신혼부부: 통장 12개월 더 (24개월). 다자녀는 신청 가능

        assertThat(ContradictionCheck.check(result,
                "청약통장을 12개월 더 유지하면 신혼부부 가입기간 요건을 채울 수 있어요.")).isEmpty();
        assertThat(ContradictionCheck.check(result,
                "청약통장을 12개월 더 유지하면 다자녀가구 요건을 채울 수 있어요.")).get().asString().contains("12");
        // 유형을 말하지 않은 문장은 보류
        assertThat(ContradictionCheck.check(result, "청약통장을 12개월 더 유지해 보세요.")).isEmpty();
    }

    @Test
    @DisplayName("형식 — 마크다운·3문장 초과는 재생성 대상, 평범한 1~2문장은 통과 (#20)")
    void formatProblem() {
        assertThat(ContradictionCheck.formatProblem(
                "청약통장을 3개월 더 유지하면 생애최초 요건을 채울 수 있어요. 그때 다시 판정해 보세요.")).isEmpty();
        assertThat(ContradictionCheck.formatProblem("**생애최초** 통장을 유지하세요.")).isPresent();
        assertThat(ContradictionCheck.formatProblem("- 통장을 유지하세요.\n- 다시 판정하세요.")).isPresent();
        assertThat(ContradictionCheck.formatProblem("하나. 둘. 셋. 넷.")).get().asString().contains("4");
        assertThat(ContradictionCheck.formatProblem(" ")).isPresent();
        // 가린 금액 표시를 그대로 옮기면 화면에 "[금액]"이 나온다 (Groq eval 2/2 회)
        assertThat(ContradictionCheck.formatProblem("청약통장 예치금을 [금액] 더 채우면 요건을 충족해요.")).isPresent();
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
