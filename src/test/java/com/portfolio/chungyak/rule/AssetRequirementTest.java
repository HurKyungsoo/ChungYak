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

    private static ApplicantProfile assets(Long total, Integer car) {
        return ApplicantProfile.builder().totalAssets(total).carValue(car).build();
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
    @DisplayName("공공주택 — 총자산 3.79억 이내 PASS, 초과 FAIL")
    void publicTotalAssetsBoundary() {
        Announcement pub = announcement(HouseDetailType.PUBLIC);
        assertThat(req.check(assets(379_000_000L, 0), pub).passed()).isTrue();
        assertThat(req.check(assets(379_000_001L, 0), pub).passed()).isFalse();
    }

    @Test
    @DisplayName("공공주택 — 자동차가액 3,708만 이내 PASS, 초과 FAIL")
    void publicCarValueBoundary() {
        Announcement pub = announcement(HouseDetailType.PUBLIC);
        assertThat(req.check(assets(100_000_000L, 37_080_000), pub).passed()).isTrue();
        assertThat(req.check(assets(100_000_000L, 40_000_000), pub).passed()).isFalse();
    }

    @Test
    @DisplayName("공공주택인데 자산 정보가 없으면 MISSING")
    void publicMissingAssets() {
        Announcement pub = announcement(HouseDetailType.PUBLIC);
        assertThat(req.check(assets(null, null), pub).status()).isEqualTo(Status.MISSING);
        assertThat(req.check(assets(100_000_000L, null), pub).reason()).isEqualTo("자동차가액");
    }

    @Test
    @DisplayName("금액 문자열이 억/만원 단위로 읽기 쉽게 나온다")
    void wonFormat() {
        assertThat(AssetRequirement.won(379_000_000L)).isEqualTo("3억 7,900만원");
        assertThat(AssetRequirement.won(37_080_000L)).isEqualTo("3,708만원");
    }
}
