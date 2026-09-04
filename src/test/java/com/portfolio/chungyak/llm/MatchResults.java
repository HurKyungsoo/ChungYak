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

    /**
     * 규제지역 공고. 다자녀는 자격 있음(신청 가능), 신혼부부는 청약통장 가입기간이 요건에 미달.
     * → hasAnyMatch() == true 이면서 failed 근거에 "12개월 / 요건(24개월)" 두 수치가 들어간다.
     * 개선 경로 설명(3a)이 근거 안 수치만으로 조언을 만들 수 있는지 확인하는 데 쓴다.
     */
    static MatchResult shortfall() {
        Announcement a = announcement(SupplyBreakdown.builder()
                .newlywed(20).multiChild(15).build(), true);
        ApplicantProfile p = RuleTestSupport.passingIncomeAndAssets()
                .married(true).monthsSinceMarriage(36).childCount(3)
                .houseless(true).accountMonths(12).everOwnedHouse(false).build();
        return ENGINE.evaluate(p, a);
    }

    /**
     * 혼인기간이 7년(84개월)을 넘어 신혼부부는 탈락하지만, 생애최초는 조건을 채운다.
     * → hasAnyMatch() == true, 신혼부부 failed 근거에 "개월 / 7년(84개월) 초과" 가 들어간다.
     */
    static MatchResult marriageExpired() {
        Announcement a = announcement(SupplyBreakdown.builder()
                .newlywed(20).firstTime(25).build());
        ApplicantProfile p = RuleTestSupport.passingIncomeAndAssets()
                .married(true).monthsSinceMarriage(100).childCount(1)
                .houseless(true).accountMonths(24).everOwnedHouse(false).build();
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
        return announcement(breakdown, false);
    }

    private static Announcement announcement(SupplyBreakdown breakdown, boolean regulated) {
        Announcement a = Announcement.builder()
                .externalId("TEST-" + breakdown.total() + (regulated ? "-R" : ""))
                .houseManageNo("2026000419").pblancNo("2026000419")
                .houseName("테스트아파트 1단지")
                .houseType(HouseType.APT)
                .houseDetailType(HouseDetailType.PRIVATE)
                .regionName("서울")
                .receptBeginDate(LocalDate.now().plusDays(7))
                .receptEndDate(LocalDate.now().plusDays(9))
                .regulationFlags(RegulationFlags.builder().speculationOverheated(regulated).build())
                .build();
        a.addUnitType(UnitType.builder()
                .modelNo("01").typeName("084.9730A")
                .generalSupplyCount(100).specialSupplyCount(breakdown.total())
                .supplyBreakdown(breakdown).build());
        return a;
    }
}
