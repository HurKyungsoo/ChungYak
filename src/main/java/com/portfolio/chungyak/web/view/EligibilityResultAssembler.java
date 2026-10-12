package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.domain.UnitType;
import com.portfolio.chungyak.rule.CommonRequirements;
import com.portfolio.chungyak.rule.EligibilityDecision;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import com.portfolio.chungyak.rule.EligibilityEngine.UnitMatch;
import com.portfolio.chungyak.rule.RequirementCheck;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MatchResult -> 화면 모델 변환.
 *
 * 순수 재배열이다. 여기서 자격을 다시 따지거나 이유를 만들어내지 않는다 —
 * 그건 rule 패키지가 이미 했고, 이 클래스는 그 결과를 옮겨 담기만 한다.
 */
@Component
public class EligibilityResultAssembler {

    public EligibilityResultView assemble(MatchResult result) {
        boolean regulated = result.announcement().getRegulationFlags() != null
                && result.announcement().getRegulationFlags().isRegulatedArea();

        return new EligibilityResultView(
                result.announcement().getId(),
                result.announcement().getHouseName(),
                regulated,
                result.hasAnyMatch(),
                result.matches().stream().map(this::toMatchedUnitType).toList(),
                result.qualifiedButUnavailable().stream()
                        .map(type -> toTypeDecision(result.decisions().get(type), result.announcement()))
                        .toList(),
                result.decisions().values().stream()
                        .map(d -> toTypeDecision(d, result.announcement()))
                        .sorted(Comparator.comparingInt(EligibilityResultAssembler::cardOrder))
                        .toList());
    }

    /**
     * 근거 카드 순서 — 충족 → 정보 필요 → 미충족. 안정 정렬이라 같은 묶음 안에서는 엔진 순서를 지킨다.
     * 판정을 바꾸는 게 아니라 "넣을 수 있는 것"을 먼저 보이게 줄만 세운다.
     */
    private static int cardOrder(EligibilityResultView.TypeDecision d) {
        if (d.eligible()) return 0;
        if (d.undetermined()) return 1;
        return 2;
    }

    private EligibilityResultView.MatchedUnitType toMatchedUnitType(UnitMatch match) {
        List<EligibilityResultView.AllocatedType> allocated = match.applicableTypes().stream()
                .map(type -> new EligibilityResultView.AllocatedType(
                        type.getLabel(),
                        match.unitType().getSupplyBreakdown().countOf(type)))
                .toList();

        return new EligibilityResultView.MatchedUnitType(
                match.unitType().getTypeName(),
                match.unitType().getSupplyArea(),
                match.allocationCountKnown(),
                match.totalAllocated(),
                allocated);
    }

    /**
     * 이 공고의 이 유형 배정 세대(주택형 합계). 세대수를 아는 주택형이 하나도 없으면(LH) null —
     * "유형 있음" 표시(1)를 세대로 보이지 않는다. 판정이 아니라 공고 데이터를 옮겨 담을 뿐이다.
     */
    private static Integer allocated(Announcement a, SpecialSupplyType type) {
        List<UnitType> known = a.getUnitTypes().stream()
                .filter(u -> u.getSupplyBreakdown() != null && u.getSupplyBreakdown().isCountsKnown())
                .toList();
        if (known.isEmpty()) return null;
        return known.stream().mapToInt(u -> u.getSupplyBreakdown().countOf(type)).sum();
    }

    private EligibilityResultView.TypeDecision toTypeDecision(EligibilityDecision decision, Announcement announcement) {
        List<RequirementCheck> common = decision.getCommonChecks();

        List<CommonCheckView> commonViews = new ArrayList<>();
        for (int i = 0; i < common.size(); i++) {
            RequirementCheck c = common.get(i);
            commonViews.add(new CommonCheckView(
                    CommonRequirements.KIND_ORDER.get(i).getLabel(),
                    c.status().name(), c.reason(), c.improvementHint()));
        }

        // 공통요건 문장은 규칙이 만든 그대로라, 같은 문장이면 정말 같은 요건이다 —
        // 이 문장을 유형별 목록에서 빼는 건 새 판정이 아니라 이미 요약 레일에 나온 걸
        // 화면에 두 번 강조하지 않으려는 것뿐이다.
        Set<String> commonReasons = common.stream().map(RequirementCheck::reason).collect(Collectors.toSet());
        Set<String> commonHints = common.stream().map(RequirementCheck::improvementHint)
                .filter(h -> h != null).collect(Collectors.toSet());

        return new EligibilityResultView.TypeDecision(
                decision.getType().name(),
                decision.getType().getLabel(),
                decision.isEligible(),
                decision.isUndetermined(),
                List.copyOf(decision.getSatisfiedReasons()),
                List.copyOf(decision.getFailedReasons()),
                List.copyOf(decision.getMissingInputs()),
                List.copyOf(decision.getImprovementHints()),
                commonViews,
                decision.getSatisfiedReasons().stream().filter(r -> !commonReasons.contains(r)).toList(),
                decision.getFailedReasons().stream().filter(r -> !commonReasons.contains(r)).toList(),
                decision.getMissingInputs().stream().filter(r -> !commonReasons.contains(r)).toList(),
                decision.getImprovementHints().stream().filter(h -> !commonHints.contains(h)).toList(),
                allocated(announcement, decision.getType()),
                decision.isFixable());
    }
}
