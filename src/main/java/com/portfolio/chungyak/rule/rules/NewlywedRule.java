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
 * 신혼부부 특별공급.
 *
 * 기본 요건
 *  - 혼인기간 7년(84개월) 이내
 *  - 무주택 세대구성원
 *  - 청약통장 가입 6개월 이상 (규제지역은 24개월)
 *  - 소득 요건 (도시근로자 대비 %, 맞벌이 완화) — {@link IncomeRequirement}
 *  - 공공주택이면 자산 요건 — {@link AssetRequirement}
 *
 * 주의: 예비신혼부부·한부모가족 등 예외는 다루지 않는다. 화면에도 그렇게 고지한다.
 */
@Component
public class NewlywedRule implements EligibilityRule {

    private static final int MAX_MARRIAGE_MONTHS = 84;
    private static final int MIN_ACCOUNT_MONTHS = 6;
    private static final int MIN_ACCOUNT_MONTHS_REGULATED = 24;

    private final IncomeRequirement incomeRequirement;
    private final AssetRequirement assetRequirement;

    public NewlywedRule(IncomeRequirement incomeRequirement, AssetRequirement assetRequirement) {
        this.incomeRequirement = incomeRequirement;
        this.assetRequirement = assetRequirement;
    }

    @Override
    public SpecialSupplyType supportedType() {
        return SpecialSupplyType.NEWLYWED;
    }

    @Override
    public EligibilityDecision evaluate(ApplicantProfile profile, Announcement announcement) {
        if (!profile.isMarried()) {
            return EligibilityDecision.ineligible(supportedType())
                    .failed("혼인 상태가 아닙니다. 신혼부부 특별공급은 혼인 중인 세대가 대상입니다.");
        }

        if (profile.getMonthsSinceMarriage() == null) {
            return EligibilityDecision.ineligible(supportedType())
                    .missing("혼인신고일로부터 경과 기간");
        }

        int marriageMonths = profile.getMonthsSinceMarriage();
        boolean marriageOk = marriageMonths <= MAX_MARRIAGE_MONTHS;
        boolean houselessOk = profile.isHouseless();

        int requiredAccount = announcement.getRegulationFlags() != null
                && announcement.getRegulationFlags().isRegulatedArea()
                ? MIN_ACCOUNT_MONTHS_REGULATED
                : MIN_ACCOUNT_MONTHS;

        Integer accountMonths = profile.getAccountMonths();
        boolean accountOk = accountMonths != null && accountMonths >= requiredAccount;

        RequirementCheck income = incomeRequirement.check(profile, supportedType());
        RequirementCheck asset = assetRequirement.check(profile, announcement);

        boolean pass = marriageOk && houselessOk && accountOk && income.passed() && asset.passed();
        EligibilityDecision decision = pass
                ? EligibilityDecision.eligible(supportedType())
                : EligibilityDecision.ineligible(supportedType());

        if (marriageOk) {
            decision.satisfied("혼인기간 " + marriageMonths + "개월로 7년 이내입니다.");
        } else {
            decision.failed("혼인기간이 " + marriageMonths + "개월로 7년(84개월)을 초과했습니다.");
        }

        if (houselessOk) {
            decision.satisfied("무주택 세대구성원 요건을 충족합니다.");
        } else {
            decision.failed("주택을 소유하고 있어 무주택 요건을 충족하지 못합니다.");
        }

        if (accountMonths == null) {
            decision.missing("청약통장 가입 기간");
        } else if (accountOk) {
            decision.satisfied("청약통장 가입 " + accountMonths + "개월로 요건("
                    + requiredAccount + "개월)을 충족합니다.");
        } else {
            decision.failed("청약통장 가입기간이 " + accountMonths + "개월로 요건("
                    + requiredAccount + "개월)에 미달합니다."
                    + (requiredAccount == MIN_ACCOUNT_MONTHS_REGULATED
                        ? " 이 공고는 규제지역이라 24개월이 필요합니다." : ""));
        }

        income.describe(decision);
        asset.describe(decision);

        return decision;
    }
}
