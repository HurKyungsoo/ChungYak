package com.portfolio.chungyak.rule.rules;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.EligibilityDecision;
import com.portfolio.chungyak.rule.EligibilityRule;
import org.springframework.stereotype.Component;

/**
 * 노부모부양 특별공급.
 *
 * 기본 요건
 *  - 만 65세 이상 직계존속을 3년 이상 계속 부양
 *  - 세대주일 것
 *  - 무주택 세대구성원
 *  - 청약통장 가입 6개월 이상 (규제지역 24개월)
 */
@Component
public class OldParentsRule implements EligibilityRule {

    private static final int MIN_ACCOUNT_MONTHS = 6;
    private static final int MIN_ACCOUNT_MONTHS_REGULATED = 24;

    @Override
    public SpecialSupplyType supportedType() {
        return SpecialSupplyType.OLD_PARENTS;
    }

    @Override
    public EligibilityDecision evaluate(ApplicantProfile profile, Announcement announcement) {
        boolean supportOk = profile.isSupportingOldParents();
        boolean headOk = profile.isHouseholdHead();
        boolean houselessOk = profile.isHouseless();

        int requiredAccount = announcement.getRegulationFlags() != null
                && announcement.getRegulationFlags().isRegulatedArea()
                ? MIN_ACCOUNT_MONTHS_REGULATED
                : MIN_ACCOUNT_MONTHS;

        Integer accountMonths = profile.getAccountMonths();
        boolean accountOk = accountMonths != null && accountMonths >= requiredAccount;

        boolean pass = supportOk && headOk && houselessOk && accountOk;
        EligibilityDecision decision = pass
                ? EligibilityDecision.eligible(supportedType())
                : EligibilityDecision.ineligible(supportedType());

        if (supportOk) {
            decision.satisfied("만 65세 이상 직계존속을 3년 이상 부양 중입니다.");
        } else {
            decision.failed("만 65세 이상 직계존속 3년 이상 부양 요건을 충족하지 못합니다.");
        }

        if (headOk) {
            decision.satisfied("세대주입니다.");
        } else {
            decision.failed("노부모부양 특별공급은 세대주만 신청할 수 있습니다.");
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
            decision.failed("청약통장 가입기간이 요건(" + requiredAccount + "개월)에 미달합니다.");
        }

        return decision;
    }
}
