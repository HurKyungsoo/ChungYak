package com.portfolio.chungyak.llm;

import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;

import java.util.List;
import java.util.Optional;

/**
 * LLM 요약이 확정된 판정과 모순되는지 사후 검사.
 *
 * 순수 함수. LLM 이 "새 판정을 하지 말라"는 지시를 어기고
 * 없는 자격을 지어내는(또는 있는 자격을 없다고 하는) 경우를 잡는다.
 *
 * 핵심 축은 {@link MatchResult#hasAnyMatch()} 다 — 이 공고에서 실제로 신청할 수
 * 있는 주택형이 있느냐. 요약이 그 극성과 반대되는 단정을 하면 모순으로 본다.
 * ("가능" 한 단어만 보지 않는다 — "불가능", "가능성" 등 오탐이 많다.)
 */
final class ContradictionCheck {

    private ContradictionCheck() {}

    /**
     * "신청할 수 없다"는 단정. CAN 판정보다 먼저 검사한다
     * ("신청 가능한 특별공급이 없습니다" 는 '신청가능' 을 포함하지만 부정문이다).
     */
    private static final List<String> CANNOT_APPLY = List.of(
            "신청가능한특별공급이없", "신청가능한특별공급유형이없", "신청가능한유형이없",
            "신청하실수있는특별공급이없", "신청할수있는특별공급이없", "해당하는특별공급이없",
            "신청할수없습니다", "신청이불가능합니다", "신청이어렵습니다",
            "자격이없습니다", "자격을충족하지못");

    /** "신청할 수 있다"는 단정 (부정문이 아닐 때만 유효) */
    private static final List<String> CAN_APPLY_STEMS = List.of(
            "신청가능", "신청하실수있", "신청할수있", "지원가능",
            "자격이됩니다", "자격이있습니다", "신청자격을충족");

    /**
     * @return 모순이 있으면 그 설명, 없으면 empty
     */
    static Optional<String> check(MatchResult result, String explanation) {
        if (explanation == null || explanation.isBlank()) {
            return Optional.of("요약이 비어 있음");
        }
        String t = explanation.replaceAll("\\s+", "");

        boolean saysCannotApply = containsAny(t, CANNOT_APPLY);
        boolean saysCanApply = !saysCannotApply && containsAny(t, CAN_APPLY_STEMS);

        if (result.hasAnyMatch() && saysCannotApply) {
            return Optional.of("신청 가능한 주택형이 있는데 요약은 '없다/불가능'이라고 단정함");
        }
        if (!result.hasAnyMatch() && saysCanApply) {
            return Optional.of("신청 가능한 주택형이 없는데 요약은 '가능하다'고 단정함");
        }
        return Optional.empty();
    }

    private static boolean containsAny(String haystack, List<String> needles) {
        return needles.stream().anyMatch(haystack::contains);
    }
}
