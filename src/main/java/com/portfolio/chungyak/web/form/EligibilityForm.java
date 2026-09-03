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

    /** 과거 주택 소유 이력 */
    private boolean everOwnedHouse;

    /** 만 65세 이상 직계존속 3년 이상 부양 여부 */
    private boolean supportingOldParents;

    /** 세대주 여부 */
    private boolean householdHead;

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
    }

    public ApplicantProfile toProfile() {
        return ApplicantProfile.builder()
                .married(married)
                .monthsSinceMarriage(monthsSinceMarriage)
                .childCount(Math.max(0, childCount))
                .hasNewborn(hasNewborn)
                .houseless(houseless)
                .accountMonths(accountMonths)
                .everOwnedHouse(everOwnedHouse)
                .supportingOldParents(supportingOldParents)
                .householdHead(householdHead)
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

    public boolean isEverOwnedHouse() { return everOwnedHouse; }
    public void setEverOwnedHouse(boolean everOwnedHouse) { this.everOwnedHouse = everOwnedHouse; }

    public boolean isSupportingOldParents() { return supportingOldParents; }
    public void setSupportingOldParents(boolean supportingOldParents) { this.supportingOldParents = supportingOldParents; }

    public boolean isHouseholdHead() { return householdHead; }
    public void setHouseholdHead(boolean householdHead) { this.householdHead = householdHead; }
}
