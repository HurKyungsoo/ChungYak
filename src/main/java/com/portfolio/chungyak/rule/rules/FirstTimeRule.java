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
 * 생애최초 특별공급.
 *
 * 기본 요건
 *  - 세대 구성원 전원이 과거 주택을 소유한 적이 없음
 *  - 무주택 세대구성원
 *  - 청약통장 가입 6개월 이상 (규제지역 24개월)
 *  - 공통 요건 (재당첨 제한 · 소득 · 자산)
 *  - 혼인 중이거나 미혼 자녀가 있을 것 (1인가구도 일부 물량 신청 가능)
 */
@Component
public class FirstTimeRule implements EligibilityRule {

    private static final int MIN_ACCOUNT_MONTHS = 6;
    private static final int MIN_ACCOUNT_MONTHS_REGULATED = 24;

    private final CommonRequirements commonRequirements;

    public FirstTimeRule(CommonRequirements commonRequirements) {
        this.commonRequirements = commonRequirements;
    }

    @Override
    public SpecialSupplyType supportedType() {
        return SpecialSupplyType.FIRST_TIME;
    }

    @Override
    public EligibilityDecision evaluate(ApplicantProfile profile, Announcement announcement) {
        // 배우자의 혼인신고 전 소유는 따지지 않는다 — 주택공급규칙 제55조의3제1항제2호(2025. 3. 31.)
        boolean onlySpousePreMarriage = profile.isEverOwnedHouse() && profile.isEverOwnedHouseOnlySpouseBeforeMarriage();
        if (profile.isEverOwnedHouse() && !onlySpousePreMarriage) {
            return EligibilityDecision.ineligible(supportedType())
                    .failed("과거 주택 소유 이력이 있어 생애최초 요건을 충족하지 못합니다. "
                            + "배우자가 혼인신고 전에 소유했던 것뿐이라면 따지지 않으니 입력에서 골라 주세요.");
        }

        int requiredAccount = announcement.getRegulationFlags() != null
                && announcement.getRegulationFlags().isRegulatedArea()
                ? MIN_ACCOUNT_MONTHS_REGULATED
                : MIN_ACCOUNT_MONTHS;

        Integer accountMonths = profile.getAccountMonths();
        boolean accountOk = accountMonths != null && accountMonths >= requiredAccount;
        boolean houselessOk = profile.isHouseless();

        List<RequirementCheck> common = commonRequirements.checkAll(profile, announcement, supportedType());

        boolean pass = accountOk && houselessOk && CommonRequirements.allPassed(common);
        EligibilityDecision decision = pass
                ? EligibilityDecision.eligible(supportedType())
                : EligibilityDecision.ineligible(supportedType());

        decision.satisfied(onlySpousePreMarriage
                ? "과거 주택 소유는 배우자의 혼인신고 전 소유뿐이라 생애최초 요건에서 따지지 않습니다 (주택공급규칙 제55조의3제1항제2호)."
                : "과거 주택 소유 이력이 없습니다.");

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
            decision.failedWithHint("청약통장 가입기간이 " + accountMonths + "개월로 요건("
                    + requiredAccount + "개월)에 미달합니다.",
                    ImprovementHints.accountMonths(accountMonths, requiredAccount));
        }

        CommonRequirements.describeAll(common, decision);

        return decision;
    }
}
