package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.RegulationFlags;
import com.portfolio.chungyak.rule.RequirementCheck.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 해당 공급지역 거주요건.
 */
class RegionResidenceRequirementTest {

    private final RegionResidenceRequirement req = RuleTestSupport.RESIDENCE;

    private static Announcement announcement(String region, boolean regulated) {
        return Announcement.builder()
                .externalId("A").houseManageNo("A").pblancNo("A").houseName("t")
                .houseType(HouseType.APT).regionName(region)
                .regulationFlags(RegulationFlags.builder().speculationOverheated(regulated).build())
                .build();
    }

    private static ApplicantProfile residence(Integer months) {
        return ApplicantProfile.builder().residenceMonthsInRegion(months).build();
    }

    @Test
    @DisplayName("비규제·비수도권 공고는 거주요건을 확인하지 않는다 (PASS)")
    void nonRegulatedNonMetroSkips() {
        assertThat(req.check(residence(null), announcement("광주", false)).status())
                .isEqualTo(Status.PASS);
    }

    @Test
    @DisplayName("투기과열지구 — 24개월 경계")
    void regulatedBoundary() {
        Announcement a = announcement("서울", true);
        assertThat(req.check(residence(23), a).passed()).isFalse();
        assertThat(req.check(residence(24), a).passed()).isTrue();
    }

    @Test
    @DisplayName("수도권 비규제 — 12개월 경계")
    void metroBoundary() {
        Announcement a = announcement("경기", false);
        assertThat(req.check(residence(11), a).passed()).isFalse();
        assertThat(req.check(residence(12), a).passed()).isTrue();
    }

    @Test
    @DisplayName("규제지역인데 거주 기간이 없으면 MISSING")
    void regulatedMissing() {
        assertThat(req.check(residence(null), announcement("서울", true)).status())
                .isEqualTo(Status.MISSING);
    }

    @Test
    @DisplayName("미충족이어도 기타지역 물량은 가능하다는 안내가 들어간다")
    void failNotesOtherRegion() {
        assertThat(req.check(residence(6), announcement("서울", true)).reason())
                .contains("기타지역");
    }
}
