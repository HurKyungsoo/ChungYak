package com.portfolio.chungyak.llm;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.rule.EligibilityDecision;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import com.portfolio.chungyak.rule.EligibilityEngine.UnitMatch;

/**
 * MatchResult -> LLM 에게 줄 "확정된 근거" 텍스트.
 *
 * 순수 함수. 여기서 판정을 다시 하지 않는다 — 이미 결정된 값을 옮겨 담기만 한다.
 * LLM 은 이 텍스트만 보고, 여기 없는 사실을 지어내면 안 된다.
 */
final class ExplanationFacts {

    private ExplanationFacts() {}

    static String format(MatchResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("아래는 규칙 엔진이 이미 확정한 판정 결과다. 이 내용만 근거로 삼아라.\n\n");

        boolean regulated = result.announcement().getRegulationFlags() != null
                && result.announcement().getRegulationFlags().isRegulatedArea();
        sb.append("공고: ").append(result.announcement().getHouseName())
                .append(regulated ? " (규제지역)" : " (비규제지역)").append('\n');

        sb.append("신청 가능한 특별공급: ");
        if (result.hasAnyMatch()) {
            sb.append('\n');
            for (UnitMatch m : result.matches()) {
                sb.append("  - 주택형 ").append(m.unitType().getTypeName()).append(": ");
                for (int i = 0; i < m.applicableTypes().size(); i++) {
                    SpecialSupplyType type = m.applicableTypes().get(i);
                    if (i > 0) sb.append(", ");
                    sb.append(type.getLabel()).append(' ')
                            .append(m.unitType().getSupplyBreakdown().countOf(type)).append("세대");
                }
                sb.append('\n');
            }
        } else {
            sb.append("없음\n");
        }

        if (!result.qualifiedButUnavailable().isEmpty()) {
            sb.append("자격은 되지만 이 공고에 물량이 없는 유형: ");
            for (int i = 0; i < result.qualifiedButUnavailable().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(result.qualifiedButUnavailable().get(i).getLabel());
            }
            sb.append('\n');
        }

        sb.append("\n유형별 판정 근거 (확정됨):\n");
        for (var entry : result.decisions().entrySet()) {
            EligibilityDecision d = entry.getValue();
            String verdict = d.isUndetermined() ? "판정 불가(입력 부족)"
                    : d.isEligible() ? "자격 있음" : "자격 없음";
            sb.append("[").append(entry.getKey().getLabel()).append("] ").append(verdict).append('\n');
            d.getSatisfiedReasons().forEach(r -> sb.append("  - 충족: ").append(r).append('\n'));
            d.getFailedReasons().forEach(r -> sb.append("  - 미충족: ").append(r).append('\n'));
            d.getMissingInputs().forEach(r -> sb.append("  - 미확인: ").append(r).append('\n'));
        }

        sb.append("\n이 근거들을 자연스러운 문장으로 정리해줘. 판정 결과나 숫자를 새로 만들지 말 것. ");
        sb.append("미충족·미확인 항목은 위 근거에 이미 있는 수치만 이용해 ");
        sb.append("얼마나 모자라는지와 무엇을 채우면 요건을 충족하는지도 덧붙여줘.");
        return sb.toString();
    }
}
