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
    @DisplayName("미달이면 '몇 개월 더' 개선 안내가 붙는다")
    void failCarriesImprovementHint() {
        RequirementCheck c = req.check(residence(20), announcement("서울", true));  // 24개월에 4개월 부족
        assertThat(c.improvementHint()).contains("4개월 더").contains("24개월");
    }

    @Test
    @DisplayName("미충족이어도 기타지역 물량은 가능하다는 안내가 들어간다")
    void failNotesOtherRegion() {
        assertThat(req.check(residence(6), announcement("서울", true)).reason())
                .contains("기타지역");
    }

    private static ApplicantProfile livingIn(String region, Integer months) {
        return ApplicantProfile.builder().residenceRegion(region).residenceMonthsInRegion(months).build();
    }

    @Test
    @DisplayName("사는 시·도가 공고와 다르면 거주 기간과 상관없이 FAIL — 비규제·비수도권 공고도")
    void otherSidoFailsEvenOutsideMetro() {
        RequirementCheck c = req.check(livingIn("서울", 120), announcement("광주", false));
        assertThat(c.status()).isEqualTo(Status.FAIL);
        assertThat(c.reason()).contains("서울").contains("광주").contains("기타지역");
    }

    @Test
    @DisplayName("같은 시·도에 살면 예전 규칙 그대로 — 비수도권은 확인하지 않고, 규제지역은 24개월 경계")
    void sameSidoKeepsExistingRule() {
        assertThat(req.check(livingIn("광주", null), announcement("광주", false)).status())
                .isEqualTo(Status.PASS);
        assertThat(req.check(livingIn("서울", 23), announcement("서울", true)).passed()).isFalse();
        assertThat(req.check(livingIn("서울", 24), announcement("서울", true)).passed()).isTrue();
    }

    @Test
    @DisplayName("공고 지역이 긴 표기(LH '경기도')여도 같은 시·도로 본다")
    void longRegionNameIsSameSido() {
        assertThat(req.check(livingIn("경기", 12), announcement("경기도", false)).passed()).isTrue();
    }
}
