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

    /** 만 6세 이하 자녀(태아 포함) 유무. 신혼희망타운은 혼인 7년 초과라도 이 조건이면 자격이 된다
     *  — hasNewborn(2세 이하)과 기준 연령이 달라 별도 필드로 둔다. */
    private final boolean hasChildUnderSix;

    /** 무주택 여부 */
    private final boolean houseless;

    /** 무주택 기간 (개월) */
    private final Integer houselessMonths;

    /** 청약통장 가입 기간 (개월) */
    private final Integer accountMonths;

    /** 청약통장 납입 횟수. 국민주택(공공) 특별공급 순위 요건에 쓴다. */
    private final Integer accountPaymentCount;

    /** 청약통장 예치금 (원). 민영주택 특별공급은 지역·면적별 예치금 기준을 넘어야 한다. */
    private final Integer accountDeposit;

    /** 과거 주택 소유 이력 — 생애최초 특공은 세대 전원이 무주택 이력이어야 한다 */
    private final boolean everOwnedHouse;

    /**
     * 과거 주택 소유가 <b>배우자의 혼인신고 전 소유뿐</b>인지 — 생애최초 특공에서는 따지지 않는다
     * (주택공급규칙 제55조의3제1항제2호, 2025. 3. 31.). {@link #everOwnedHouse} 가 true 일 때만 의미가 있다.
     */
    private final boolean everOwnedHouseOnlySpouseBeforeMarriage;

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

    /**
     * 세대 부동산 가액 합계 (원). 민영 신혼부부·생애최초·신생아 특공은 소득 기준을 넘어도 이 값이 기준 이하면
     * 추첨공급 대상이다 — {@link IncomeRequirement}. 모르면 null(소득 초과 시 MISSING).
     */
    private final Long realEstateValue;

    /** 자동차가액 (원). 공공주택 특별공급 자산 요건 — 총자산과 별개 상한. */
    private final Integer carValue;

    /** 과거에 특별공급에 당첨된 적이 있는지 (본인 또는 세대원). 특별공급은 세대당 평생 1회. */
    private final boolean everWonSpecialSupply;

    /** 과거 특별공급 당첨이 누구의·언제 것인지 — 제55조의3 특례. 모르면 OTHER(특례 없음) */
    @Builder.Default
    private final SpecialSupplyWinTiming specialSupplyWinTiming = SpecialSupplyWinTiming.OTHER;

    /**
     * 2024년 6월 19일 이후 출생한 자녀(태아, 그날 이후 출생자 입양 포함)가 있는지 — 특별공급 이력이 있어도 신생아·다자녀·
     * 신혼부부·노부모 특공을 한 차례 더 신청할 수 있다(제55조의3제3항, 2025. 3. 31.).
     */
    private final boolean hasChildBornSince20240619;

    /**
     * 2023년 3월 28일 이후 출생한 자녀 수(태아·입양 포함) — 공공주택 특별법 공공분양은 소득 기준에 %p 가산
     * (공공주택 특별법 시행규칙 [별표 6] 제3호가목). 모르면 null(가산 없음).
     */
    private final Integer childrenBornSince20230328;

    /** 마지막 당첨일로부터 경과 개월 수. null 이면 당첨 이력 없음. 재당첨 제한 기간 계산에 쓴다. */
    private final Integer monthsSinceLastWin;

    /** 과거 당첨 주택이 투기과열지구·청약과열지역이었는지 — 재당첨 제한 기간이 더 길다(10년). */
    private final boolean pastWinInSpeculationArea;

    public boolean hasChildren() {
        return childCount > 0;
    }
}
