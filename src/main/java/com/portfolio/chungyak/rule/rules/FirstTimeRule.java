package com.portfolio.chungyak.rule.rules;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.AssetRequirement;
import com.portfolio.chungyak.rule.EligibilityDecision;
import com.portfolio.chungyak.rule.EligibilityRule;
import com.portfolio.chungyak.rule.IncomeRequirement;
import com.portfolio.chungyak.rule.RequirementCheck;
import org.springframework.stereotype.Component;

/**
 * 생애최초 특별공급.
 *
 * 기본 요건
 *  - 세대 구성원 전원이 과거 주택을 소유한 적이 없음
 *  - 무주택 세대구성원
 *  - 청약통장 가입 6개월 이상 (규제지역 24개월)
 *  - 소득 요건 (도시근로자 대비 %, 맞벌이 완화)
 *  - 공공주택이면 자산 요건
 *  - 혼인 중이거나 미혼 자녀가 있을 것 (1인가구도 일부 물량 신청 가능)
 */
@Component
public class FirstTimeRule implements EligibilityRule {

    private static final int MIN_ACCOUNT_MONTHS = 6;
    private static final int MIN_ACCOUNT_MONTHS_REGULATED = 24;

    private final IncomeRequirement incomeRequirement;
    private final AssetRequirement assetRequirement;

    public FirstTimeRule(IncomeRequirement incomeRequirement, AssetRequirement assetRequirement) {
        this.incomeRequirement = incomeRequirement;
        this.assetRequirement = assetRequirement;
    }

    @Override
    public SpecialSupplyType supportedType() {
        return SpecialSupplyType.FIRST_TIME;
    }

    @Override
    public EligibilityDecision evaluate(ApplicantProfile profile, Announcement announcement) {
        if (profile.isEverOwnedHouse()) {
            return EligibilityDecision.ineligible(supportedType())
                    .failed("과거 주택 소유 이력이 있어 생애최초 요건을 충족하지 못합니다.");
        }

        int requiredAccount = announcement.getRegulationFlags() != null
                && announcement.getRegulationFlags().isRegulatedArea()
                ? MIN_ACCOUNT_MONTHS_REGULATED
                : MIN_ACCOUNT_MONTHS;

        Integer accountMonths = profile.getAccountMonths();
        boolean accountOk = accountMonths != null && accountMonths >= requiredAccount;
        boolean houselessOk = profile.isHouseless();

        RequirementCheck income = incomeRequirement.check(profile, supportedType());
        RequirementCheck asset = assetRequirement.check(profile, announcement);

        boolean pass = accountOk && houselessOk && income.passed() && asset.passed();
        EligibilityDecision decision = pass
                ? EligibilityDecision.eligible(supportedType())
                : EligibilityDecision.ineligible(supportedType());

        decision.satisfied("과거 주택 소유 이력이 없습니다.");

        if (houselessOk) {
            decision.satisfied("현재 무주택입니다.");
        } else {
            decision.failed("현재 주택을 소유하고 있습니다.");
        }

        if (accountMonths == null) {
            decision.missing("청약통장 가입 기간");
        } else if (accountOk) {
            decision.satisfied("청약통장 가입 " + accountMonths + "개월로 요건을 충족합니다.");
        } else {
            decision.failed("청약통장 가입기간이 " + accountMonths + "개월로 요건("
                    + requiredAccount + "개월)에 미달합니다.");
        }

        income.describe(decision);
        asset.describe(decision);

        return decision;
    }
}
