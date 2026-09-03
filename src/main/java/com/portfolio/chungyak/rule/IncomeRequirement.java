package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import org.springframework.stereotype.Component;

/**
 * 특별공급 소득 요건 확인 — 유형별 상한(도시근로자 대비 %)과 신청자 소득을 비교.
 *
 * 각 {@link EligibilityRule} 이 자기 판정에 이 결과를 접어 넣는다.
 * 계산은 결정론적이다 (환산 %와 상한 비교뿐).
 */
@Component
public class IncomeRequirement {

    private final IncomeReference incomeReference;
    private final SpecialSupplyRequirementProperties requirements;

    public IncomeRequirement(IncomeReference incomeReference,
                             SpecialSupplyRequirementProperties requirements) {
        this.incomeReference = incomeReference;
        this.requirements = requirements;
    }

    public RequirementCheck check(ApplicantProfile profile, SpecialSupplyType type) {
        Integer limit = requirements.incomeLimitPercent(type, profile.isDualIncome());
        if (limit == null) {
            return RequirementCheck.pass("이 유형은 별도 소득 상한이 없습니다.");
        }

        Integer percent = incomeReference.percentOf(
                profile.getMonthlyHouseholdIncome(), profile.getHouseholdSize());
        if (percent == null) {
            return RequirementCheck.missing("가구 월평균소득·가구원 수");
        }

        String basis = "가구 소득이 도시근로자 월평균소득(" + incomeReference.basisYear() + "년 기준) 대비 "
                + percent + "%";
        String bound = "요건(" + limit + "% 이하"
                + (profile.isDualIncome() ? ", 맞벌이 완화 기준" : "") + ")";

        return percent <= limit
                ? RequirementCheck.pass(basis + "로 " + bound + "을 충족합니다.")
                : RequirementCheck.fail(basis + "로 " + bound + "을 초과합니다.");
    }
}
