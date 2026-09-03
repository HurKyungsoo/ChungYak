package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.RegulationFlags;
import com.portfolio.chungyak.rule.RequirementCheck.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 청약통장 납입횟수(국민)·예치금(민영) 요건.
 */
class AccountRequirementTest {

    private final AccountRequirement req = RuleTestSupport.ACCOUNT;

    private static Announcement announcement(HouseDetailType type, String region, boolean regulated) {
        return Announcement.builder()
                .externalId("A").houseManageNo("A").pblancNo("A").houseName("t")
                .houseType(HouseType.APT).houseDetailType(type).regionName(region)
                .regulationFlags(RegulationFlags.builder().speculationOverheated(regulated).build())
                .build();
    }

    private static ApplicantProfile account(Integer count, Integer deposit) {
        return ApplicantProfile.builder().accountPaymentCount(count).accountDeposit(deposit).build();
    }

    @Test
    @DisplayName("국민주택 — 납입 12회 경계")
    void publicCountBoundary() {
        Announcement pub = announcement(HouseDetailType.PUBLIC, "서울", false);
        assertThat(req.check(account(11, null), pub).passed()).isFalse();
        assertThat(req.check(account(12, null), pub).passed()).isTrue();
    }

    @Test
    @DisplayName("국민주택 규제지역 — 24회 필요")
    void publicRegulatedNeeds24() {
        Announcement pub = announcement(HouseDetailType.PUBLIC, "서울", true);
        assertThat(req.check(account(12, null), pub).passed()).isFalse();
        assertThat(req.check(account(24, null), pub).passed()).isTrue();
    }

    @Test
    @DisplayName("국민주택인데 납입 횟수가 없으면 MISSING")
    void publicMissingCount() {
        Announcement pub = announcement(HouseDetailType.PUBLIC, "서울", false);
        assertThat(req.check(account(null, 5_000_000), pub).status()).isEqualTo(Status.MISSING);
    }

    @Test
    @DisplayName("민영주택 서울 — 예치금 300만 경계")
    void privateSeoulDepositBoundary() {
        Announcement priv = announcement(HouseDetailType.PRIVATE, "서울", false);
        assertThat(req.check(account(null, 2_999_999), priv).passed()).isFalse();
        assertThat(req.check(account(null, 3_000_000), priv).passed()).isTrue();
    }

    @Test
    @DisplayName("민영주택 기타 지역(경기) — 예치금 기준이 더 낮다 (200만)")
    void privateOtherRegionLowerThreshold() {
        Announcement gyeonggi = announcement(HouseDetailType.PRIVATE, "경기", false);
        assertThat(req.check(account(null, 2_000_000), gyeonggi).passed()).isTrue();
        assertThat(req.check(account(null, 1_999_999), gyeonggi).passed()).isFalse();
    }

    @Test
    @DisplayName("민영주택인데 예치금이 없으면 MISSING")
    void privateMissingDeposit() {
        Announcement priv = announcement(HouseDetailType.PRIVATE, "서울", false);
        assertThat(req.check(account(24, null), priv).status()).isEqualTo(Status.MISSING);
    }

    @Test
    @DisplayName("PASS 사유에 큰 평형은 예치금이 더 필요하다는 고지가 들어간다")
    void privatePassNotesLargerSizes() {
        Announcement priv = announcement(HouseDetailType.PRIVATE, "서울", false);
        assertThat(req.check(account(null, 5_000_000), priv).reason()).contains("면적이 큰 주택형");
    }
}
