package com.portfolio.chungyak.rule;

import lombok.Builder;
import lombok.Getter;

/**
 * 신청자 조건.
 *
 * LLM 이 자연어 질문에서 추출하거나, 사용자가 폼으로 직접 입력한다.
 * 어느 쪽이든 규칙 엔진은 이 정형 객체만 받는다 —
 * 판정 로직이 자연어에 노출되면 결과가 재현되지 않는다.
 */
@Getter
@Builder
public class ApplicantProfile {

    /** 만 나이 */
    private final Integer age;

    /** 혼인 여부 */
    private final boolean married;

    /** 혼인신고일로부터 경과 개월 수. 신혼부부 특공은 7년(84개월) 이내. */
    private final Integer monthsSinceMarriage;

    /** 미성년 자녀 수 */
    @Builder.Default
    private final int childCount = 0;

    /** 2세 이하 자녀(신생아) 유무 */
    private final boolean hasNewborn;

    /** 무주택 여부 */
    private final boolean houseless;

    /** 무주택 기간 (개월) */
    private final Integer houselessMonths;

    /** 청약통장 가입 기간 (개월) */
    private final Integer accountMonths;

    /** 과거 주택 소유 이력 — 생애최초 특공은 세대 전원이 무주택 이력이어야 한다 */
    private final boolean everOwnedHouse;

    /** 만 65세 이상 직계존속을 3년 이상 부양 중인지 */
    private final boolean supportingOldParents;

    /** 거주 지역명 ("서울", "경기" 등 — 공급지역명과 같은 체계) */
    private final String residenceRegion;

    /** 세대주 여부 */
    private final boolean householdHead;

    public boolean hasChildren() {
        return childCount > 0;
    }
}
