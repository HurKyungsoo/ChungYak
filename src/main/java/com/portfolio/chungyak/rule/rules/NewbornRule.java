package com.portfolio.chungyak.rule.rules;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.EligibilityDecision;
import com.portfolio.chungyak.rule.EligibilityRule;
import org.springframework.stereotype.Component;

/**
 * 신생아 특별공급.
 *
 * 공공주택에만 배정된다 — Swagger Mdl 설명에 명시돼 있고,
 * 실데이터에서도 HOUSE_DTL_SECD='03' 이면서 특별법 적용인 공고만
 * NWBB_HSHLDCO 가 채워진다. 그래서 공고 유형부터 먼저 확인한다.
 *
 * 기본 요건
 *  - 2세 이하(입양 포함) 자녀가 있을 것
 *  - 무주택 세대구성원
 */
@Component
public class NewbornRule implements EligibilityRule {

    @Override
    public SpecialSupplyType supportedType() {
        return SpecialSupplyType.NEWBORN;
    }

    @Override
    public EligibilityDecision evaluate(ApplicantProfile profile, Announcement announcement) {
        // 민영주택에는 신생아 특공 물량 자체가 없다
        if (announcement.getHouseDetailType() != HouseDetailType.PUBLIC) {
            return EligibilityDecision.ineligible(supportedType())
                    .failed("이 공고는 민영주택이라 신생아 특별공급 물량이 배정되지 않습니다.");
        }

        boolean newbornOk = profile.isHasNewborn();
        boolean houselessOk = profile.isHouseless();

        boolean pass = newbornOk && houselessOk;
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

        return decision;
    }
}
