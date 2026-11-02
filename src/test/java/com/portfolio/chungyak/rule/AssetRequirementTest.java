package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.rule.RequirementCheck.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 특별공급 자산 요건 확인 — 공공주택만 적용.
 */
class AssetRequirementTest {

    private final AssetRequirement req = RuleTestSupport.ASSET;

    private static Announcement announcement(HouseDetailType type) {
        return Announcement.builder()
                .externalId("A").houseManageNo("A").pblancNo("A").houseName("t")
                .houseType(HouseType.APT).houseDetailType(type).build();
    }

    private static ApplicantProfile assets(Long realEstate, Integer car) {
        return ApplicantProfile.builder().realEstateValue(realEstate).carValue(car).build();
    }

    @Test
    @DisplayName("민영주택은 자산 요건이 없어 항상 PASS")
    void privateAlwaysPasses() {
        RequirementCheck c = req.check(assets(9_999_999_999L, 999_999_999),
                announcement(HouseDetailType.PRIVATE));
        assertThat(c.status()).isEqualTo(Status.PASS);
        assertThat(c.reason()).contains("민영");
    }

    @Test
    @DisplayName("공공주택 — 세대 부동산 가액 2억 1,550만 이내 PASS, 초과 FAIL")
    void publicRealEstateBoundary() {
        Announcement pub = announcement(HouseDetailType.PUBLIC);
        assertThat(req.check(assets(215_500_000L, 0), pub).passed()).isTrue();
        assertThat(req.check(assets(215_500_001L, 0), pub).passed()).isFalse();
    }

    @Test
    @DisplayName("공공주택 — 자동차가액 4,542만 이내 PASS, 초과 FAIL")
    void publicCarValueBoundary() {
        Announcement pub = announcement(HouseDetailType.PUBLIC);
        assertThat(req.check(assets(100_000_000L, 45_420_000), pub).passed()).isTrue();
        assertThat(req.check(assets(100_000_000L, 45_420_001), pub).passed()).isFalse();
    }

    @Test
    @DisplayName("총자산이 커도 부동산·자동차가 기준 안이면 PASS — 공공분양은 총자산 기준이 아니다")
    void totalAssetsIgnored() {
        ApplicantProfile p = ApplicantProfile.builder()
                .totalAssets(900_000_000L).realEstateValue(100_000_000L).carValue(10_000_000).build();
        assertThat(req.check(p, announcement(HouseDetailType.PUBLIC)).passed()).isTrue();
    }

    @Test
    @DisplayName("2023.3.28 이후 출생 자녀 — 1명이면 10%p, 2명 이상이거나 그 전 자녀도 있으면 20%p 완화 금액")
    void birthRelaxation() {
        Announcement pub = announcement(HouseDetailType.PUBLIC);
        ApplicantProfile.ApplicantProfileBuilder oneRecent = ApplicantProfile.builder()
                .childCount(1).childrenBornSince20230328(1).carValue(0);
        assertThat(req.check(oneRecent.realEstateValue(237_050_000L).build(), pub).passed()).isTrue();
        assertThat(req.check(oneRecent.realEstateValue(237_050_001L).build(), pub).passed()).isFalse();

        ApplicantProfile.ApplicantProfileBuilder withOlder = ApplicantProfile.builder()
                .childCount(2).childrenBornSince20230328(1).realEstateValue(100_000_000L);
        RequirementCheck at = req.check(withOlder.carValue(54_510_000).build(), pub);
        assertThat(at.passed()).isTrue();
        assertThat(at.reason()).contains("완화");
        assertThat(req.check(withOlder.carValue(54_510_001).build(), pub).passed()).isFalse();
    }

    @Test
    @DisplayName("초과하면 얼마나 넘었는지 금액도 적는다 (#19-1b)")
    void failReasonCarriesGap() {
        Announcement pub = announcement(HouseDetailType.PUBLIC);
        assertThat(req.check(assets(250_000_000L, 0), pub).reason()).endsWith("— 3,450만원 초과.");
        assertThat(req.check(assets(100_000_000L, 50_000_000), pub).reason()).endsWith("— 458만원 초과.");
    }

    @Test
    @DisplayName("공공주택인데 자산 정보가 없으면 MISSING")
    void publicMissingAssets() {
        Announcement pub = announcement(HouseDetailType.PUBLIC);
        assertThat(req.check(assets(null, null), pub).reason()).isEqualTo("세대 부동산 가액");
        assertThat(req.check(assets(null, null), pub).status()).isEqualTo(Status.MISSING);
        assertThat(req.check(assets(100_000_000L, null), pub).reason()).isEqualTo("자동차가액");
    }

    @Test
    @DisplayName("금액 문자열이 억/만원 단위로 읽기 쉽게 나온다")
    void wonFormat() {
        assertThat(AssetRequirement.won(215_500_000L)).isEqualTo("2억 1,550만원");
        assertThat(AssetRequirement.won(45_420_000L)).isEqualTo("4,542만원");
    }
}
