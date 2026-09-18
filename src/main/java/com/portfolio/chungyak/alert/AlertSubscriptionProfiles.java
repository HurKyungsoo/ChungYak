package com.portfolio.chungyak.alert;

import com.portfolio.chungyak.domain.AlertSubscription;
import com.portfolio.chungyak.rule.ApplicantProfile;

/**
 * {@code AlertSubscription} -> {@code ApplicantProfile} — {@code EligibilityForm.toProfile()}
 * 와 같은 매핑이다. 새 공고 알림·마감 임박 알림 두 배치가 똑같이 필요해서 여기 하나로 묶는다
 * (규칙마다 복붙 금지 원칙을 배치 코드에도 적용).
 */
final class AlertSubscriptionProfiles {

    private AlertSubscriptionProfiles() {}

    static ApplicantProfile toProfile(AlertSubscription s) {
        return ApplicantProfile.builder()
                .married(s.isMarried())
                .monthsSinceMarriage(s.getMonthsSinceMarriage())
                .childCount(Math.max(0, s.getChildCount()))
                .hasNewborn(s.isHasNewborn())
                .hasChildUnderSix(s.isHasChildUnderSix())
                .houseless(s.isHouseless())
                .accountMonths(s.getAccountMonths())
                .accountPaymentCount(s.getAccountPaymentCount())
                .accountDeposit(s.getAccountDeposit())
                .everOwnedHouse(s.isEverOwnedHouse())
                .supportingOldParents(s.isSupportingOldParents())
                .householdHead(s.isHouseholdHead())
                .residenceMonthsInRegion(s.getResidenceMonthsInRegion())
                .monthlyHouseholdIncome(s.getMonthlyHouseholdIncome())
                .householdSize(s.getHouseholdSize())
                .dualIncome(s.isDualIncome())
                .totalAssets(s.getTotalAssets())
                .carValue(s.getCarValue())
                .everWonSpecialSupply(s.isEverWonSpecialSupply())
                .monthsSinceLastWin(s.getMonthsSinceLastWin())
                .pastWinInSpeculationArea(s.isPastWinInSpeculationArea())
                .build();
    }
}
