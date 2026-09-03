package com.portfolio.chungyak.rule.rules;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.CommonRequirements;
import com.portfolio.chungyak.rule.EligibilityDecision;
import com.portfolio.chungyak.rule.EligibilityRule;
import com.portfolio.chungyak.rule.RequirementCheck;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 신생아 특별공급.
 *
 * 기본 요건
 *  - 2세 이하(입양 포함) 자녀가 있을 것
 *  - 무주택 세대구성원
 *  - 공통 요건 (재당첨 제한 · 소득 · 자산 — 신생아는 소득 완화폭이 크다)
 *
 * 공급 대상: 2024년 제도 개편으로 공공·민영 모두에 배정된다.
 * 라이브 데이터로 확인 — 민영 공고 49건, 205개 주택형에 NWBB_HSHLDCO 가 채워져 있다.
 * 그래서 이 규칙은 공고 유형을 보지 않는다 — "이 공고 주택형에 신생아 물량이 있는가"는
 * EligibilityEngine 이 SupplyBreakdown 으로 판단하고, 이 규칙은 신청자 조건만 본다.
 */
@Component
public class NewbornRule implements EligibilityRule {

    private final CommonRequirements commonRequirements;

    public NewbornRule(CommonRequirements commonRequirements) {
        this.commonRequirements = commonRequirements;
    }

    @Override
    public SpecialSupplyType supportedType() {
        return SpecialSupplyType.NEWBORN;
    }

    @Override
    public EligibilityDecision evaluate(ApplicantProfile profile, Announcement announcement) {
        boolean newbornOk = profile.isHasNewborn();
        boolean houselessOk = profile.isHouseless();

        List<RequirementCheck> common = commonRequirements.checkAll(profile, announcement, supportedType());

        boolean pass = newbornOk && houselessOk && CommonRequirements.allPassed(common);
        EligibilityDecision decision = pass
                ? EligibilityDecision.eligible(supportedType())
                : EligibilityDecision.ineligible(supportedType());

        if (newbornOk) {
            decision.satisfied("2세 이하 자녀가 있습니다.");
        } else {
            decision.failed("2세 이하 자녀가 없어 신생아 특별공급 대상이 아닙니다.");
        }

        if (houselessOk) {
            decision.satisfied("무주택 요건을 충족합니다.");
        } else {
            decision.failed("주택을 소유하고 있습니다.");
        }

        CommonRequirements.describeAll(common, decision);

        return decision;
    }
}
