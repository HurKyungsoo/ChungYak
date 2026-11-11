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
 * 특별공급 자산 요건 확인 — 공공주택 특별법 공고만 적용(민영·그 밖의 국민주택은 자산 요건 없음).
 */
class AssetRequirementTest {

    private final AssetRequirement req = RuleTestSupport.ASSET;

    private static Announcement announcement(HouseDetailType type) {
        return Announcement.builder()
                .externalId("A").houseManageNo("A").pblancNo("A").houseName("t")
                .houseType(HouseType.APT).houseDetailType(type)
                .regulationFlags(RegulationFlags.builder().publicHousingSpecialLaw(type == HouseDetailType.PUBLIC).build())
                .build();
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
    @DisplayName("공공주택 특별법이 아닌 국민주택은 자산 요건이 없다 — 주택공급규칙에 조항이 없고 공고문도 소득(초과 시 부동산 추첨)만 본다")
    void nationalHousingHasNoAssetLimit() {
        Announcement national = Announcement.builder()
                .externalId("N").houseManageNo("N").pblancNo("N").houseName("e편한세상 답십리 아르테포레(국민주택)")
                .houseType(HouseType.APT).houseDetailType(HouseDetailType.PUBLIC)
                .regulationFlags(RegulationFlags.builder().publicHousingSpecialLaw(false).build())
                .build();
        RequirementCheck c = req.check(assets(900_000_000L, 200_000_000), national);
        assertThat(c.status()).isEqualTo(Status.PASS);
        assertThat(c.reason()).contains("국민주택").contains("자산 요건이 없습니다");
        assertThat(req.check(assets(null, null), national).status()).isEqualTo(Status.PASS);   // 입력이 없어도 물을 게 없다
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

    private static Announcement named(String houseName, HouseDetailType type) {
        return Announcement.builder()
                .externalId("A").houseManageNo("A").pblancNo("A").houseName(houseName)
                .houseType(HouseType.APT).houseDetailType(type)
                .regulationFlags(RegulationFlags.builder().publicHousingSpecialLaw(type == HouseDetailType.PUBLIC).build())
                .build();
    }

    @Test
    @DisplayName("토지임대부·지분적립형 — 세대 총자산+자동차 3억 6,200만 이내 PASS, 초과 FAIL (부동산·자동차 따로 보지 않는다)")
    void totalAssetBoundary() {
        Announcement landLease = named("삼도이동 2지구 토지임대부 공공분양주택", HouseDetailType.PUBLIC);
        Announcement equity = named("광교 자연앤 센트레빌(A17BL) 지분적립형 분양주택", HouseDetailType.PUBLIC);
        for (Announcement a : new Announcement[]{landLease, equity}) {
            assertThat(a.usesTotalAssetLimit()).isTrue();
            ApplicantProfile at = ApplicantProfile.builder().totalAssets(332_000_000L).carValue(30_000_000).build();
            RequirementCheck c = req.check(at, a);
            assertThat(c.passed()).isTrue();
            assertThat(c.reason()).contains("3억 6,200만원").contains("총자산");
            RequirementCheck over = req.check(ApplicantProfile.builder().totalAssets(332_000_001L).carValue(30_000_000).build(), a);
            assertThat(over.status()).isEqualTo(Status.FAIL);
            assertThat(over.reason()).contains("초과");
        }
        // 일반형이면 부동산 3억이라 탈락할 세대도 총자산 기준 안이면 통과 — 부동산 가액 칸은 보지 않는다
        ApplicantProfile bigHouse = ApplicantProfile.builder()
                .realEstateValue(300_000_000L).totalAssets(300_000_000L).carValue(10_000_000).build();
        assertThat(req.check(bigHouse, landLease).passed()).isTrue();
        assertThat(req.check(bigHouse, announcement(HouseDetailType.PUBLIC)).passed()).isFalse();
    }

    @Test
    @DisplayName("토지임대부·지분적립형 — 2023.3.28 이후 출생 자녀 1명 3억 9,700만, 2명 이상 4억 3,100만")
    void totalAssetBirthRelaxation() {
        Announcement a = named("삼도이동 1지구 토지임대부 공공분양주택", HouseDetailType.PUBLIC);
        ApplicantProfile.ApplicantProfileBuilder one = ApplicantProfile.builder().childCount(1).childrenBornSince20230328(1).carValue(0);
        assertThat(req.check(one.totalAssets(397_000_000L).build(), a).passed()).isTrue();
        assertThat(req.check(one.totalAssets(397_000_001L).build(), a).passed()).isFalse();
        ApplicantProfile.ApplicantProfileBuilder two = ApplicantProfile.builder().childCount(2).childrenBornSince20230328(2).carValue(0);
        RequirementCheck at = req.check(two.totalAssets(431_000_000L).build(), a);
        assertThat(at.passed()).isTrue();
        assertThat(at.reason()).contains("완화");
        assertThat(req.check(two.totalAssets(431_000_001L).build(), a).passed()).isFalse();
    }

    @Test
    @DisplayName("토지임대부·지분적립형인데 총자산·자동차가 비면 MISSING — 부동산 가액으로 대신하지 않는다")
    void totalAssetMissing() {
        Announcement a = named("삼도이동 2지구 토지임대부 공공분양주택", HouseDetailType.PUBLIC);
        assertThat(req.check(ApplicantProfile.builder().realEstateValue(0L).carValue(0).build(), a).status()).isEqualTo(Status.MISSING);
        assertThat(req.check(ApplicantProfile.builder().totalAssets(0L).build(), a).status()).isEqualTo(Status.MISSING);
    }

    @Test
    @DisplayName("공고명으로 가르는 건 공공분양만 — 민영이나 일반형 공공분양은 해당 없음")
    void totalAssetOnlyForNamedPublic() {
        assertThat(named("○○ 지분적립형", HouseDetailType.PRIVATE).usesTotalAssetLimit()).isFalse();
        assertThat(named("시흥거모지구 S-1블록 공공분양(본청약)", HouseDetailType.PUBLIC).usesTotalAssetLimit()).isFalse();
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
