package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import org.springframework.stereotype.Component;

/**
 * 특별공급 소득 요건 확인 — 유형별 상한(도시근로자 대비 %)과 신청자 소득을 비교.
 *
 * 상한은 <b>공고의 주택 구분</b>에 따라 다르다(주택공급규칙, 2026-06-15 현행 — 2026-10-15 원문 확인):
 * 민영주택은 {@link SpecialSupplyRequirementProperties#privateIncomeLimitPercent}(다자녀·노부모는 소득 기준 없음,
 * 생애최초·신생아 160%), 공공(국민)주택은 {@link SpecialSupplyRequirementProperties#incomeLimitPercent}.
 * 민영 신혼부부·생애최초·신생아는 소득을 넘어도 세대 부동산 가액이 기준 이하면 추첨공급 대상이다 — 그 값을 모르면 MISSING.
 *
 * 각 {@link EligibilityRule} 이 자기 판정에 이 결과를 접어 넣는다. 계산은 결정론적이다 (환산 %와 상한 비교뿐).
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

    /** 공고 구분을 모를 때(신혼희망타운 — 늘 공공) — 공공 기준 */
    public RequirementCheck check(ApplicantProfile profile, SpecialSupplyType type) {
        return check(profile, type, null);
    }

    public RequirementCheck check(ApplicantProfile profile, SpecialSupplyType type, Announcement announcement) {
        boolean privateHousing = announcement != null && announcement.getHouseDetailType() == HouseDetailType.PRIVATE;
        Integer limit = privateHousing
                ? requirements.privateIncomeLimitPercent(type, profile.isDualIncome())
                : requirements.incomeLimitPercent(type, profile.isDualIncome());
        if (limit == null) {
            return RequirementCheck.pass(privateHousing
                    ? "민영주택 " + type.getLabel() + " 특별공급은 소득 기준이 없습니다."
                    : "이 유형은 별도 소득 상한이 없습니다.");
        }

        Integer percent = incomeReference.percentOf(
                profile.getMonthlyHouseholdIncome(), profile.getHouseholdSize());
        if (percent == null) {
            return RequirementCheck.missing("가구 월평균소득·가구원 수");
        }

        String basis = "가구 소득이 도시근로자 월평균소득(" + incomeReference.basisYear() + "년 기준) 대비 "
                + percent + "%";
        boolean dualRelaxed = profile.isDualIncome() && !limit.equals(privateHousing
                ? requirements.privateIncomeLimitPercent(type, false) : requirements.incomeLimitPercent(type, false));
        String bound = "요건(" + limit + "% 이하" + (dualRelaxed ? ", 맞벌이 완화 기준" : "") + ")";

        if (percent <= limit) {
            return RequirementCheck.pass(basis + "로 " + bound + "을 충족합니다.");
        }

        // 민영 신혼부부·생애최초·신생아 — 소득을 넘어도 부동산 가액 기준 이하면 추첨공급 대상
        if (privateHousing && requirements.hasRealEstateTrack(type)) {
            long reLimit = requirements.realEstateLimit();
            Long realEstate = profile.getRealEstateValue();
            String track = "소득 " + bound + "은 넘지만, 세대 부동산 가액이 " + AssetRequirement.won(reLimit)
                    + " 이하면 추첨공급 대상입니다";
            if (realEstate == null) {
                return RequirementCheck.missing("세대 부동산 가액(" + basis + "로 소득 기준 초과 — "
                        + AssetRequirement.won(reLimit) + " 이하면 추첨공급 대상)");
            }
            return realEstate <= reLimit
                    ? RequirementCheck.pass(basis + "로 " + track + " — 부동산 가액 " + AssetRequirement.won(realEstate)
                            + "로 충족합니다(추첨공급).")
                    : RequirementCheck.fail(basis + "로 " + track + " — 부동산 가액 " + AssetRequirement.won(realEstate)
                            + "로 기준을 넘습니다.");
        }

        // 얼마나 넘었는지 금액으로도 — 같은 기준표에서 상한 금액을 구해 차이만 적는다(#19-1b)
        Long limitWon = incomeReference.limitAmount(profile.getHouseholdSize(), limit);
        String gap = limitWon == null ? ""
                : " 상한은 월 " + AssetRequirement.won(limitWon) + "으로 월 "
                  + AssetRequirement.won(profile.getMonthlyHouseholdIncome() - limitWon) + " 초과입니다.";
        return RequirementCheck.fail(basis + "로 " + bound + "을 초과합니다." + gap);
    }
}
