package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.rule.EligibilityDecision;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import com.portfolio.chungyak.rule.EligibilityEngine.UnitMatch;
import org.springframework.stereotype.Component;

import java.util.List;

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
        return new EligibilityResultView.TypeDecision(
                decision.getType().getLabel(),
                decision.isEligible(),
                decision.isUndetermined(),
                List.copyOf(decision.getSatisfiedReasons()),
                List.copyOf(decision.getFailedReasons()),
                List.copyOf(decision.getMissingInputs()),
                List.copyOf(decision.getImprovementHints()));
    }
}
