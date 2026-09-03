package com.portfolio.chungyak.llm;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.RegulationFlags;
import com.portfolio.chungyak.domain.SupplyBreakdown;
import com.portfolio.chungyak.domain.UnitType;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import com.portfolio.chungyak.rule.RuleTestSupport;

import java.time.LocalDate;

/**
 * 요약/모순검사 테스트용 MatchResult 생성기.
 * 실제 규칙 엔진을 돌려 만든다 (판정은 결정론적이므로 고정).
 */
final class MatchResults {

    private static final EligibilityEngine ENGINE = new EligibilityEngine(RuleTestSupport.allRules());

    private MatchResults() {}

    /** 신혼·생애최초 신청 가능 → hasAnyMatch() == true */
    static MatchResult withMatch() {
        Announcement a = announcement(SupplyBreakdown.builder()
                .newlywed(47).firstTime(22).multiChild(31).build());
        ApplicantProfile p = RuleTestSupport.passingIncomeAndAssets()
                .married(true).monthsSinceMarriage(36).houseless(true)
                .accountMonths(12).everOwnedHouse(false).build();
        return ENGINE.evaluate(p, a);
    }

    /** 아무 유형도 자격 없음 → hasAnyMatch() == false, qualifiedButUnavailable 도 비어 있음 */
    static MatchResult noMatch() {
        Announcement a = announcement(SupplyBreakdown.builder()
                .newlywed(47).firstTime(22).build());
        ApplicantProfile p = RuleTestSupport.passingIncomeAndAssets()
                .married(false).houseless(false).accountMonths(0)
                .everOwnedHouse(true).build();
        return ENGINE.evaluate(p, a);
    }

    /** 다자녀 자격은 되지만 공고에 다자녀 물량 0 → qualifiedButUnavailable 에 다자녀 */
    static MatchResult qualifiedButUnavailable() {
        Announcement a = announcement(SupplyBreakdown.builder()
                .newlywed(47).build());   // multiChild = 0
        ApplicantProfile p = RuleTestSupport.passingIncomeAndAssets()
                .married(true).monthsSinceMarriage(36).childCount(2)
                .houseless(true).accountMonths(12).everOwnedHouse(false).build();
        return ENGINE.evaluate(p, a);
    }

    private static Announcement announcement(SupplyBreakdown breakdown) {
        Announcement a = Announcement.builder()
                .externalId("TEST-" + breakdown.total())
                .houseManageNo("2026000419").pblancNo("2026000419")
                .houseName("테스트아파트 1단지")
                .houseType(HouseType.APT)
                .houseDetailType(HouseDetailType.PRIVATE)
                .regionName("서울")
                .receptBeginDate(LocalDate.now().plusDays(7))
                .receptEndDate(LocalDate.now().plusDays(9))
                .regulationFlags(RegulationFlags.builder().build())
                .build();
        a.addUnitType(UnitType.builder()
                .modelNo("01").typeName("084.9730A")
                .generalSupplyCount(100).specialSupplyCount(breakdown.total())
                .supplyBreakdown(breakdown).build());
        return a;
    }
}
