package com.portfolio.chungyak.web.form;

import com.portfolio.chungyak.llm.ExtractedProfile;
import com.portfolio.chungyak.rule.ApplicantProfile;

/**
 * 자격 판정 입력 폼.
 *
 * 이 클래스가 하는 일은 "폼 필드 -> ApplicantProfile" 변환뿐이고, 그 변환은
 * 완전히 결정론적이다(계산도 조건 분기도 없다). 판정 로직은 여기 들어오지 않는다.
 *
 * 나중에 이 자리의 일부는 LLM 이 대신한다 — 자연어 질문에서 같은 필드를 뽑아
 * ApplicantProfile 을 만든다. 어느 쪽이든 규칙 엔진에는 정형 객체만 넘어간다.
 *
 * Lombok @Setter 는 프로젝트 규칙상 쓰지 않으므로 접근자를 직접 둔다
 * (Thymeleaf 폼 바인딩에 setter 가 필요하다).
 */
public class EligibilityForm {

    /** 혼인 여부 */
    private boolean married;

    /** 혼인신고일로부터 경과 개월 수 (혼인 상태일 때만 의미) */
    private Integer monthsSinceMarriage;

    /** 미성년 자녀 수 */
    private int childCount;

    /** 2세 이하 자녀(신생아) 유무 */
    private boolean hasNewborn;

    /** 무주택 여부 */
    private boolean houseless;

    /** 청약통장 가입 기간 (개월) */
    private Integer accountMonths;

    /** 청약통장 납입 횟수 (국민주택) */
    private Integer accountPaymentCount;

    /** 청약통장 예치금 (원, 민영주택) */
    private Integer accountDeposit;

    /** 과거 주택 소유 이력 */
    private boolean everOwnedHouse;

    /** 만 65세 이상 직계존속 3년 이상 부양 여부 */
    private boolean supportingOldParents;

    /** 세대주 여부 */
    private boolean householdHead;

    /** 해당 공급지역 계속 거주 기간 (개월) */
    private Integer residenceMonthsInRegion;

    /** 가구 월평균소득 (원, 세전) */
    private Integer monthlyHouseholdIncome;

    /** 가구원 수 (본인 포함) */
    private Integer householdSize;

    /** 맞벌이 여부 */
    private boolean dualIncome;

    /** 총자산 (원) — 공공주택 특공 자산 요건 */
    private Long totalAssets;

    /** 자동차가액 (원) — 공공주택 특공 자산 요건 */
    private Integer carValue;

    /** 과거 특별공급 당첨 이력 (본인/세대원) — 특별공급은 평생 1회 */
    private boolean everWonSpecialSupply;

    /** 마지막 당첨일로부터 경과 개월 (없으면 비움) */
    private Integer monthsSinceLastWin;

    /** 과거 당첨 주택이 투기과열지구·청약과열지역이었는지 */
    private boolean pastWinInSpeculationArea;

    /**
     * LLM 이 뽑은 값 중 <b>null 이 아닌 것만</b> 폼에 채운다.
     * null(= LLM 이 확인 못 한 값)은 건드리지 않는다 — 사용자가 화면에서 직접 고른다.
     * 여기에 판단은 없다. 값 복사뿐이다.
     */
    public void applyExtracted(ExtractedProfile e) {
        if (e.married() != null) this.married = e.married();
        if (e.monthsSinceMarriage() != null) this.monthsSinceMarriage = e.monthsSinceMarriage();
        if (e.childCount() != null) this.childCount = Math.max(0, e.childCount());
        if (e.hasNewborn() != null) this.hasNewborn = e.hasNewborn();
        if (e.houseless() != null) this.houseless = e.houseless();
        if (e.accountMonths() != null) this.accountMonths = e.accountMonths();
        if (e.everOwnedHouse() != null) this.everOwnedHouse = e.everOwnedHouse();
        if (e.supportingOldParents() != null) this.supportingOldParents = e.supportingOldParents();
        if (e.householdHead() != null) this.householdHead = e.householdHead();
        // 소득·자산·거주기간은 아직 LLM 추출 대상이 아니다(B1b 에서 ExtractedProfile 확장 시 추가).
    }

    public ApplicantProfile toProfile() {
        return ApplicantProfile.builder()
                .married(married)
                .monthsSinceMarriage(monthsSinceMarriage)
                .childCount(Math.max(0, childCount))
                .hasNewborn(hasNewborn)
                .houseless(houseless)
                .accountMonths(accountMonths)
                .accountPaymentCount(accountPaymentCount)
                .accountDeposit(accountDeposit)
                .everOwnedHouse(everOwnedHouse)
                .supportingOldParents(supportingOldParents)
                .householdHead(householdHead)
                .residenceMonthsInRegion(residenceMonthsInRegion)
                .monthlyHouseholdIncome(monthlyHouseholdIncome)
                .householdSize(householdSize)
                .dualIncome(dualIncome)
                .totalAssets(totalAssets)
                .carValue(carValue)
                .everWonSpecialSupply(everWonSpecialSupply)
                .monthsSinceLastWin(monthsSinceLastWin)
                .pastWinInSpeculationArea(pastWinInSpeculationArea)
                .build();
    }

    public boolean isMarried() { return married; }
    public void setMarried(boolean married) { this.married = married; }

    public Integer getMonthsSinceMarriage() { return monthsSinceMarriage; }
    public void setMonthsSinceMarriage(Integer monthsSinceMarriage) { this.monthsSinceMarriage = monthsSinceMarriage; }

    public int getChildCount() { return childCount; }
    public void setChildCount(int childCount) { this.childCount = childCount; }

    public boolean isHasNewborn() { return hasNewborn; }
    public void setHasNewborn(boolean hasNewborn) { this.hasNewborn = hasNewborn; }

    public boolean isHouseless() { return houseless; }
    public void setHouseless(boolean houseless) { this.houseless = houseless; }

    public Integer getAccountMonths() { return accountMonths; }
    public void setAccountMonths(Integer accountMonths) { this.accountMonths = accountMonths; }

    public Integer getAccountPaymentCount() { return accountPaymentCount; }
    public void setAccountPaymentCount(Integer accountPaymentCount) { this.accountPaymentCount = accountPaymentCount; }

    public Integer getAccountDeposit() { return accountDeposit; }
    public void setAccountDeposit(Integer accountDeposit) { this.accountDeposit = accountDeposit; }

    public boolean isEverOwnedHouse() { return everOwnedHouse; }
    public void setEverOwnedHouse(boolean everOwnedHouse) { this.everOwnedHouse = everOwnedHouse; }

    public boolean isSupportingOldParents() { return supportingOldParents; }
    public void setSupportingOldParents(boolean supportingOldParents) { this.supportingOldParents = supportingOldParents; }

    public boolean isHouseholdHead() { return householdHead; }
    public void setHouseholdHead(boolean householdHead) { this.householdHead = householdHead; }

    public Integer getResidenceMonthsInRegion() { return residenceMonthsInRegion; }
    public void setResidenceMonthsInRegion(Integer residenceMonthsInRegion) { this.residenceMonthsInRegion = residenceMonthsInRegion; }

    public Integer getMonthlyHouseholdIncome() { return monthlyHouseholdIncome; }
    public void setMonthlyHouseholdIncome(Integer monthlyHouseholdIncome) { this.monthlyHouseholdIncome = monthlyHouseholdIncome; }

    public Integer getHouseholdSize() { return householdSize; }
    public void setHouseholdSize(Integer householdSize) { this.householdSize = householdSize; }

    public boolean isDualIncome() { return dualIncome; }
    public void setDualIncome(boolean dualIncome) { this.dualIncome = dualIncome; }

    public Long getTotalAssets() { return totalAssets; }
    public void setTotalAssets(Long totalAssets) { this.totalAssets = totalAssets; }

    public Integer getCarValue() { return carValue; }
    public void setCarValue(Integer carValue) { this.carValue = carValue; }

    public boolean isEverWonSpecialSupply() { return everWonSpecialSupply; }
    public void setEverWonSpecialSupply(boolean everWonSpecialSupply) { this.everWonSpecialSupply = everWonSpecialSupply; }

    public Integer getMonthsSinceLastWin() { return monthsSinceLastWin; }
    public void setMonthsSinceLastWin(Integer monthsSinceLastWin) { this.monthsSinceLastWin = monthsSinceLastWin; }

    public boolean isPastWinInSpeculationArea() { return pastWinInSpeculationArea; }
    public void setPastWinInSpeculationArea(boolean pastWinInSpeculationArea) { this.pastWinInSpeculationArea = pastWinInSpeculationArea; }
}
