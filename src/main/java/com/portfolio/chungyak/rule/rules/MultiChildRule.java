package com.portfolio.chungyak.rule.rules;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.CommonRequirements;
import com.portfolio.chungyak.rule.EligibilityDecision;
import com.portfolio.chungyak.rule.EligibilityRule;
import com.portfolio.chungyak.rule.ImprovementHints;
import com.portfolio.chungyak.rule.RequirementCheck;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 다자녀가구 특별공급.
 *
 * 기본 요건
 *  - 미성년 자녀 2명 이상 (2024년 기준 3명 -> 2명으로 완화)
 *  - 무주택 세대구성원
 *  - 청약통장 가입 6개월 이상
 *  - 공통 요건 (재당첨 제한 · 소득 · 자산)
 */
@Component
public class MultiChildRule implements EligibilityRule {

    private static final int MIN_CHILDREN = 2;
    private static final int MIN_ACCOUNT_MONTHS = 6;

    private final CommonRequirements commonRequirements;

    public MultiChildRule(CommonRequirements commonRequirements) {
        this.commonRequirements = commonRequirements;
    }

    @Override
    public SpecialSupplyType supportedType() {
        return SpecialSupplyType.MULTI_CHILD;
    }

    @Override
    public EligibilityDecision evaluate(ApplicantProfile profile, Announcement announcement) {
        boolean childOk = profile.getChildCount() >= MIN_CHILDREN;
        boolean houselessOk = profile.isHouseless();

        Integer accountMonths = profile.getAccountMonths();
        boolean accountOk = accountMonths != null && accountMonths >= MIN_ACCOUNT_MONTHS;

        List<RequirementCheck> common = commonRequirements.checkAll(profile, announcement, supportedType());

        boolean pass = childOk && houselessOk && accountOk && CommonRequirements.allPassed(common);
        EligibilityDecision decision = pass
                ? EligibilityDecision.eligible(supportedType())
                : EligibilityDecision.ineligible(supportedType());

        if (childOk) {
            decision.satisfied("미성년 자녀 " + profile.getChildCount() + "명으로 요건을 충족합니다.");
        } else {
            decision.failed("미성년 자녀가 " + profile.getChildCount()
                    + "명으로 요건(2명 이상)에 미달합니다.");
        }

        if (houselessOk) {
            decision.satisfied("무주택 요건을 충족합니다.");
        } else {
            decision.failed("주택을 소유하고 있습니다.");
        }

        if (accountMonths == null) {
            decision.missing("청약통장 가입 기간");
        } else if (accountOk) {
            decision.satisfied("청약통장 가입 " + accountMonths + "개월입니다.");
        } else {
            decision.failed("청약통장 가입기간이 " + accountMonths + "개월로 6개월에 미달합니다.")
                    .hint(ImprovementHints.accountMonths(accountMonths, MIN_ACCOUNT_MONTHS));
        }

        CommonRequirements.describeAll(common, decision);

        return decision;
    }
}
