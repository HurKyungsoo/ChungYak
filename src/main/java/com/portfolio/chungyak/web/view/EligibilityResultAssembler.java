package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.rule.CommonRequirements;
import com.portfolio.chungyak.rule.EligibilityDecision;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import com.portfolio.chungyak.rule.EligibilityEngine.UnitMatch;
import com.portfolio.chungyak.rule.RequirementCheck;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
                        .map(type -> toTypeDecision(result.decisions().get(type)))
                        .toList(),
                result.decisions().values().stream()
                        .map(this::toTypeDecision)
                        .toList());
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

    private EligibilityResultView.TypeDecision toTypeDecision(EligibilityDecision decision) {
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
                decision.getImprovementHints().stream().filter(h -> !commonHints.contains(h)).toList());
    }
}
