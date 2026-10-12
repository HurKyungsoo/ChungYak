package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.RegulationFlags;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.domain.SupplyBreakdown;
import com.portfolio.chungyak.domain.UnitType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EligibilityDecision#isFixable()} — "이것만 바뀌면 가능"을 화면이 아니라 엔진이 판정한다 (#19-1b).
 * 미충족이 전부 기간·선택으로 메울 수 있는 것일 때만 참이어야 한다.
 */
class FixableDecisionTest {

    private final EligibilityEngine engine = new EligibilityEngine(RuleTestSupport.allRules());

    private static Announcement privateApt() {
        Announcement a = Announcement.builder().externalId("T").houseManageNo("T").pblancNo("T").houseName("t")
                .houseType(HouseType.APT).houseDetailType(HouseDetailType.PRIVATE)
                .regulationFlags(RegulationFlags.builder().build()).build();
        a.addUnitType(UnitType.builder().modelNo("01").typeName("84A")
                .supplyBreakdown(SupplyBreakdown.builder().newlywed(40).build()).build());
        return a;
    }

    private EligibilityDecision newlywed(ApplicantProfile p) {
        return engine.evaluate(p, privateApt()).decisions().get(SpecialSupplyType.NEWLYWED);
    }

    @Test
    @DisplayName("통장 기간만 모자라면 fixable — 기간이 지나면 채워진다")
    void onlyAccountShortIsFixable() {
        EligibilityDecision d = newlywed(RuleTestSupport.passingIncomeAndAssets()
                .married(true).monthsSinceMarriage(24).houseless(true).accountMonths(3)
                .accountDeposit(3_000_000).build());

        assertThat(d.isEligible()).isFalse();
        assertThat(d.isFixable()).isTrue();
        assertThat(d.getImprovementHints()).anyMatch(h -> h.contains("3개월 더"));
    }

    @Test
    @DisplayName("혼인 7년 초과(메울 수 없음)가 섞이면 통장이 모자라도 fixable 아님 — 실제로 본 오독 사례")
    void unfixableFailureMakesWholeDecisionUnfixable() {
        EligibilityDecision d = newlywed(RuleTestSupport.passingIncomeAndAssets()
                .married(true).monthsSinceMarriage(100).houseless(true).accountMonths(3)
                .accountDeposit(3_000_000).build());

        assertThat(d.isEligible()).isFalse();
        assertThat(d.getImprovementHints()).isNotEmpty();   // 통장 힌트는 여전히 있다
        assertThat(d.isFixable()).isFalse();
    }

    @Test
    @DisplayName("자격 충족·입력 부족(판정 보류)은 fixable 아님")
    void eligibleOrUndeterminedIsNotFixable() {
        EligibilityDecision ok = newlywed(RuleTestSupport.passingIncomeAndAssets()
                .married(true).monthsSinceMarriage(24).houseless(true).accountMonths(24)
                .accountDeposit(3_000_000).build());
        assertThat(ok.isEligible()).isTrue();
        assertThat(ok.isFixable()).isFalse();

        EligibilityDecision missing = newlywed(RuleTestSupport.passingIncomeAndAssets()
                .married(true).houseless(true).build());   // 혼인 개월·통장 없음
        assertThat(missing.isUndetermined()).isTrue();
        assertThat(missing.isFixable()).isFalse();
    }

    @Test
    @DisplayName("failedWithHint 에 힌트가 null 이면 메울 수 없는 미충족으로 센다")
    void nullHintCountsAsUnfixable() {
        EligibilityDecision d = EligibilityDecision.ineligible(SpecialSupplyType.NEWLYWED)
                .failedWithHint("무언가 미달", null);
        assertThat(d.isFixable()).isFalse();
        assertThat(d.getImprovementHints()).isEmpty();
    }
}
