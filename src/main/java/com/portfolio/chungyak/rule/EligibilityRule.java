package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.SpecialSupplyType;

/**
 * 특별공급 유형별 자격 판정 규칙.
 *
 * 유형마다 별도 구현체를 두는 이유: 요건이 서로 완전히 달라서
 * if-else 로 몰면 한 유형을 고칠 때 다른 유형이 깨진다.
 * 새 유형이 생기면 구현체를 추가하기만 하면 된다.
 */
public interface EligibilityRule {

    SpecialSupplyType supportedType();

    /**
     * @param profile      신청자 조건
     * @param announcement 공고 (규제지역 여부 등 공고별 조건 반영)
     */
    EligibilityDecision evaluate(ApplicantProfile profile, Announcement announcement);
}
