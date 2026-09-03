package com.portfolio.chungyak.llm;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import com.portfolio.chungyak.rule.EligibilityEngine.UnitMatch;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LLM 요약을 못 쓸 때(비활성·실패·모순) 대신 보여줄 결정론적 한 문단.
 *
 * 순수 함수. MatchResult 의 값만으로 조립하므로 판정과 절대 어긋나지 않는다.
 */
final class FallbackSummary {

    private FallbackSummary() {}

    static String of(MatchResult result) {
        String house = result.announcement().getHouseName();

        if (result.hasAnyMatch()) {
            String types = mergedAllocations(result).entrySet().stream()
                    .map(e -> e.getKey().getLabel() + " " + e.getValue() + "세대")
                    .collect(Collectors.joining(", "));
            StringBuilder sb = new StringBuilder(
                    "‘" + house + "’ 공고에서 현재 조건으로 신청 가능한 특별공급은 " + types + "입니다.");
            if (!result.qualifiedButUnavailable().isEmpty()) {
                sb.append(" ").append(labels(result))
                        .append("은(는) 자격은 되지만 이 공고에 배정된 물량이 없습니다.");
            }
            return sb.toString();
        }

        StringBuilder sb = new StringBuilder(
                "‘" + house + "’ 공고에서 현재 조건으로 신청 가능한 특별공급이 없습니다.");
        if (!result.qualifiedButUnavailable().isEmpty()) {
            sb.append(" 다만 ").append(labels(result))
                    .append("은(는) 자격 자체는 충족하므로 물량이 있는 다른 공고에서 신청할 수 있습니다.");
        }
        sb.append(" 자세한 사유는 아래 판정 근거를 확인하세요.");
        return sb.toString();
    }

    /** 주택형마다 갈린 배정 세대를 유형별로 합산 */
    private static Map<SpecialSupplyType, Integer> mergedAllocations(MatchResult result) {
        Map<SpecialSupplyType, Integer> merged = new LinkedHashMap<>();
        for (UnitMatch m : result.matches()) {
            for (SpecialSupplyType type : m.applicableTypes()) {
                merged.merge(type, m.unitType().getSupplyBreakdown().countOf(type), Integer::sum);
            }
        }
        return merged;
    }

    private static String labels(MatchResult result) {
        return result.qualifiedButUnavailable().stream()
                .map(SpecialSupplyType::getLabel)
                .collect(Collectors.joining(", "));
    }
}
