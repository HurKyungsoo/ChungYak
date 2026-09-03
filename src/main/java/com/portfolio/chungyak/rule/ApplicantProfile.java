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

    /** 해당 공급지역 계속 거주 기간 (개월). 1순위·우선공급 요건에 쓰인다. */
    private final Integer residenceMonthsInRegion;

    /** 세대주 여부 */
    private final boolean householdHead;

    /** 가구 월평균소득 (원, 세전). 도시근로자 월평균소득 대비 %로 환산해 소득 요건과 비교한다. */
    private final Integer monthlyHouseholdIncome;

    /** 가구원 수 (본인 포함). 소득 기준표가 가구원 수별로 다르다. */
    private final Integer householdSize;

    /** 맞벌이 여부. 맞벌이는 소득 기준이 완화된다(예: 100% → 120%). */
    private final boolean dualIncome;

    /** 총자산 (원). 공공주택 특별공급만 자산 요건이 있다(민영은 소득만). */
    private final Long totalAssets;

    /** 자동차가액 (원). 공공주택 특별공급 자산 요건 — 총자산과 별개 상한. */
    private final Integer carValue;

    /** 과거에 특별공급에 당첨된 적이 있는지 (본인 또는 세대원). 특별공급은 세대당 평생 1회. */
    private final boolean everWonSpecialSupply;

    /** 마지막 당첨일로부터 경과 개월 수. null 이면 당첨 이력 없음. 재당첨 제한 기간 계산에 쓴다. */
    private final Integer monthsSinceLastWin;

    /** 과거 당첨 주택이 투기과열지구·청약과열지역이었는지 — 재당첨 제한 기간이 더 길다(10년). */
    private final boolean pastWinInSpeculationArea;

    public boolean hasChildren() {
        return childCount > 0;
    }
}
