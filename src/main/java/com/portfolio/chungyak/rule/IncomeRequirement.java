package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import org.springframework.stereotype.Component;

/**
 * 특별공급 소득 요건 확인 — 유형별 상한(도시근로자 대비 %)과 신청자 소득을 비교.
 *
 * 상한은 <b>공고에 적용되는 법</b>에 따라 다르다({@link #housingRuleOf}, 2026-10-15 원문 확인):
 * <ul>
 *   <li>민영주택 — 주택공급에 관한 규칙(2026-06-15): 다자녀·노부모는 소득 기준 없음, 생애최초·신생아 160%</li>
 *   <li>국민주택 — 같은 규칙: 신혼부부 140/160, 생애최초·신생아 130</li>
 *   <li>공공주택 특별법 공공분양 — 시행규칙 [별표 6](2026-06-22): 맞벌이 200%, 2023. 3. 28. 이후 출생 자녀 가산</li>
 * </ul>
 * 민영·국민주택 신혼부부·생애최초·신생아는 소득을 넘어도 세대 부동산 가액이 기준 이하면 추첨공급 대상이다 — 그 값을 모르면 MISSING.
 *
 * 각 {@link EligibilityRule} 이 자기 판정에 이 결과를 접어 넣는다. 계산은 결정론적이다 (환산 %와 상한 비교뿐).
 */
@Component
public class IncomeRequirement {

    /** 공고에 적용되는 소득 기준의 근거 법 */
    public enum HousingRule {
        PRIVATE("민영주택"),
        NATIONAL("국민주택"),
        PUBLIC_HOUSING("공공주택 특별법 공공분양");

        private final String label;

        HousingRule(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final IncomeReference incomeReference;
    private final SpecialSupplyRequirementProperties requirements;

    public IncomeRequirement(IncomeReference incomeReference,
                             SpecialSupplyRequirementProperties requirements) {
        this.incomeReference = incomeReference;
        this.requirements = requirements;
    }

    /**
     * 민영(HOUSE_DTL_SECD=01) / 공공주택 특별법(청약홈 PUBLIC_HOUSE_SPCLW_APPLC_AT=Y, 또는 LH — LH API 는 이 표시를 주지 않지만
     * LH 는 공공주택사업자다) / 그 밖의 국민주택. 공고를 모르면(신혼희망타운 — 늘 공공) 공공주택 특별법.
     */
    public static HousingRule housingRuleOf(Announcement announcement) {
        if (announcement == null) return HousingRule.PUBLIC_HOUSING;
        if (announcement.getHouseDetailType() == HouseDetailType.PRIVATE) return HousingRule.PRIVATE;
        boolean specialLaw = announcement.getRegulationFlags() != null
                && announcement.getRegulationFlags().isPublicHousingSpecialLaw();
        return specialLaw || announcement.isLh() ? HousingRule.PUBLIC_HOUSING : HousingRule.NATIONAL;
    }

    /** 공고 구분을 모를 때(신혼희망타운 — 늘 공공) — 공공주택 특별법 기준 */
    public RequirementCheck check(ApplicantProfile profile, SpecialSupplyType type) {
        return check(profile, type, null);
    }

    public RequirementCheck check(ApplicantProfile profile, SpecialSupplyType type, Announcement announcement) {
        HousingRule rule = housingRuleOf(announcement);
        Integer base = limitFor(rule, type, profile.isDualIncome());
        if (base == null) {
            return RequirementCheck.pass(rule == HousingRule.PRIVATE
                    ? "민영주택 " + type.getLabel() + " 특별공급은 소득 기준이 없습니다."
                    : "이 유형은 별도 소득 상한이 없습니다.");
        }
        int bonus = rule == HousingRule.PUBLIC_HOUSING ? birthBonus(profile) : 0;
        int limit = base + bonus;

        Integer percent = incomeReference.percentOf(
                profile.getMonthlyHouseholdIncome(), profile.getHouseholdSize());
        if (percent == null) {
            return RequirementCheck.missing("가구 월평균소득·가구원 수");
        }

        String basis = "가구 소득이 도시근로자 월평균소득(" + incomeReference.basisYear() + "년 기준) 대비 "
                + percent + "%";
        Integer single = limitFor(rule, type, false);
        boolean dualRelaxed = profile.isDualIncome() && single != null && !single.equals(base);
        String bound = "요건(" + limit + "% 이하"
                + (dualRelaxed ? ", 맞벌이 완화 기준" : "")
                + (bonus > 0 ? ", 2023.3.28 이후 출생 자녀 +" + bonus + "%p" : "") + ")";

        if (percent <= limit) {
            return RequirementCheck.pass(basis + "로 " + bound + "을 충족합니다.");
        }

        // 민영·국민주택 신혼부부·생애최초·신생아 — 소득을 넘어도 부동산 가액 기준 이하면 추첨공급 대상
        if (rule != HousingRule.PUBLIC_HOUSING && requirements.hasRealEstateTrack(type)) {
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

    private Integer limitFor(HousingRule rule, SpecialSupplyType type, boolean dual) {
        return switch (rule) {
            case PRIVATE -> requirements.privateIncomeLimitPercent(type, dual);
            case NATIONAL -> requirements.nationalIncomeLimitPercent(type, dual);
            case PUBLIC_HOUSING -> requirements.incomeLimitPercent(type, dual);
        };
    }

    /**
     * 2023. 3. 28. 이후 출생(태아·입양 포함) 자녀 가산 %p — [별표 6] 제3호가목.
     * 그 자녀만 1명이면 one, 2명 이상이거나 1명이고 그 전에 출생한 자녀(미성년 자녀 수 − 그 자녀 수)가 있으면 twoOrMore.
     */
    int birthBonus(ApplicantProfile profile) {
        int recent = profile.getChildrenBornSince20230328() == null ? 0 : Math.max(0, profile.getChildrenBornSince20230328());
        if (recent == 0) return 0;
        boolean older = profile.getChildCount() > recent;
        return recent >= 2 || older ? requirements.birthBonus().twoOrMore() : requirements.birthBonus().one();
    }
}
