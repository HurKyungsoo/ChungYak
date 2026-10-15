package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.rule.RequirementCheck.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 특별공급 소득 요건 확인.
 */
class IncomeRequirementTest {

    private final IncomeRequirement req = RuleTestSupport.INCOME;

    private static ApplicantProfile profile(int income, int size, boolean dual) {
        return ApplicantProfile.builder()
                .monthlyHouseholdIncome(income).householdSize(size).dualIncome(dual)
                .build();
    }

    @Test
    @DisplayName("소득이 유형 상한 이내면 PASS, 초과면 FAIL")
    void withinAndOverLimit() {
        // 3인 도시근로자 7,198,649원. 공공주택 특별법 신혼 상한 130% = 9,358,244원.
        assertThat(req.check(profile(7_000_000, 3, false), SpecialSupplyType.NEWLYWED, PUBLIC).status())
                .isEqualTo(Status.PASS);
        assertThat(req.check(profile(11_000_000, 3, false), SpecialSupplyType.NEWLYWED, PUBLIC).status())
                .isEqualTo(Status.FAIL);
    }

    @Test
    @DisplayName("초과하면 상한 금액과 초과 금액을 적는다 — 같은 기준표·같은 산수 (#19-1b)")
    void failReasonCarriesGap() {
        // 공공주택 특별법 신혼 130%: 3인 7,198,649원 × 130% = 9,358,244원 → 935만원. 11,000,000 - 9,358,244 = 1,641,756 → 164만원
        assertThat(req.check(profile(11_000_000, 3, false), SpecialSupplyType.NEWLYWED, PUBLIC).reason())
                .endsWith("상한은 월 935만원으로 월 164만원 초과입니다.");
        // 통과하면 금액을 덧붙이지 않는다
        assertThat(req.check(profile(7_000_000, 3, false), SpecialSupplyType.NEWLYWED, PUBLIC).reason())
                .doesNotContain("상한은 월");
    }

    @Test
    @DisplayName("맞벌이는 완화된 상한을 쓴다")
    void dualIncomeUsesRelaxedLimit() {
        // 약 146% — 단독 140 초과, 맞벌이 160 이내
        assertThat(req.check(profile(10_500_000, 3, false), SpecialSupplyType.NEWLYWED, NATIONAL).passed()).isFalse();
        assertThat(req.check(profile(10_500_000, 3, true), SpecialSupplyType.NEWLYWED, NATIONAL).passed()).isTrue();
    }

    @Test
    @DisplayName("다자녀·노부모는 맞벌이 완화가 없다 (single = dual = 120)")
    void multiChildNoDualRelaxation() {
        // 약 121% — 120 초과. 맞벌이여도 그대로 FAIL
        assertThat(req.check(profile(8_700_000, 3, true), SpecialSupplyType.MULTI_CHILD, NATIONAL).passed()).isFalse();
    }

    @Test
    @DisplayName("소득·가구원 수가 없으면 MISSING")
    void missingInput() {
        RequirementCheck c = req.check(ApplicantProfile.builder().build(), SpecialSupplyType.NEWLYWED);
        assertThat(c.status()).isEqualTo(Status.MISSING);
        assertThat(c.reason()).contains("소득");
    }

    @Test
    @DisplayName("이유 문장에 % 와 상한이 들어간다")
    void reasonCarriesNumbers() {
        RequirementCheck c = req.check(profile(7_000_000, 3, false), SpecialSupplyType.NEWLYWED, NATIONAL);
        assertThat(c.reason()).contains("%").contains("140% 이하");
    }

    // ── 공고 주택 구분별 상한 (주택공급규칙 2026-06-15 현행 — 2026-10-15 원문 확인) ──

    private static com.portfolio.chungyak.domain.Announcement housing(com.portfolio.chungyak.domain.HouseDetailType t) {
        return com.portfolio.chungyak.domain.Announcement.builder().houseDetailType(t).build();
    }

    private static final com.portfolio.chungyak.domain.Announcement PRIVATE = housing(com.portfolio.chungyak.domain.HouseDetailType.PRIVATE);
    /** 공공주택 특별법 표시 없는 국민주택 — 주택공급규칙 기준(신혼 140/160, 다자녀 120) */
    private static final com.portfolio.chungyak.domain.Announcement NATIONAL = housing(com.portfolio.chungyak.domain.HouseDetailType.PUBLIC);
    /** 공공주택 특별법 공공분양 — [별표 6] 기준(맞벌이 200%) */
    private static final com.portfolio.chungyak.domain.Announcement PUBLIC = com.portfolio.chungyak.domain.Announcement.builder()
            .houseDetailType(com.portfolio.chungyak.domain.HouseDetailType.PUBLIC)
            .regulationFlags(com.portfolio.chungyak.domain.RegulationFlags.builder().publicHousingSpecialLaw(true).build()).build();

    @Test
    @DisplayName("민영 다자녀·노부모는 소득 기준이 없다(제40조①·제46조①) — 공공은 120% 그대로")
    void privateMultiChildAndOldParentsHaveNoIncomeLimit() {
        ApplicantProfile rich = profile(21_600_000, 3, false);   // 약 300%
        for (SpecialSupplyType t : java.util.List.of(SpecialSupplyType.MULTI_CHILD, SpecialSupplyType.OLD_PARENTS)) {
            RequirementCheck c = req.check(rich, t, PRIVATE);
            assertThat(c.status()).as(t.name()).isEqualTo(Status.PASS);
            assertThat(c.reason()).contains("민영주택").contains("소득 기준이 없습니다");
            assertThat(req.check(rich, t, PUBLIC).status()).as(t.name() + " 공공").isEqualTo(Status.FAIL);
        }
    }

    @Test
    @DisplayName("민영 생애최초·신생아는 160%(맞벌이 구분 없음) — 경계 160 통과, 161 은 부동산 추첨 경로로")
    void privateFirstTimeAndNewborn160() {
        ApplicantProfile at160 = profile(11_517_838, 3, false);
        ApplicantProfile at161 = profile(11_600_000, 3, true);   // 맞벌이여도 160 그대로
        for (SpecialSupplyType t : java.util.List.of(SpecialSupplyType.FIRST_TIME, SpecialSupplyType.NEWBORN)) {
            assertThat(req.check(at160, t, PRIVATE).status()).as(t.name()).isEqualTo(Status.PASS);
            assertThat(req.check(at160, t, PRIVATE).reason()).contains("160% 이하").doesNotContain("맞벌이");
            assertThat(req.check(at161, t, PRIVATE).status()).as(t.name()).isEqualTo(Status.MISSING);
        }
        // 155% 외벌이 — 민영 생애최초는 통과(예전엔 130% 로 FAIL 이었다)
        assertThat(req.check(profile(11_160_000, 3, false), SpecialSupplyType.FIRST_TIME, PRIVATE).status()).isEqualTo(Status.PASS);
    }

    @Test
    @DisplayName("민영 소득 초과 — 부동산 가액 모르면 MISSING, 3.31억 이하면 PASS(추첨공급), 넘으면 FAIL. 다자녀 등 경로 없는 유형은 해당 없음")
    void privateRealEstateTrack() {
        ApplicantProfile.ApplicantProfileBuilder over = ApplicantProfile.builder()
                .monthlyHouseholdIncome(20_000_000).householdSize(3).dualIncome(false);
        RequirementCheck unknown = req.check(over.build(), SpecialSupplyType.NEWBORN, PRIVATE);
        assertThat(unknown.status()).isEqualTo(Status.MISSING);
        assertThat(unknown.reason()).contains("세대 부동산 가액").contains("3억 3,100만원");

        assertThat(req.check(over.realEstateValue(331_000_000L).build(), SpecialSupplyType.NEWBORN, PRIVATE).status())
                .isEqualTo(Status.PASS);
        assertThat(req.check(over.realEstateValue(331_000_000L).build(), SpecialSupplyType.NEWBORN, PRIVATE).reason())
                .contains("추첨공급");
        assertThat(req.check(over.realEstateValue(331_000_001L).build(), SpecialSupplyType.NEWBORN, PRIVATE).status())
                .isEqualTo(Status.FAIL);
        // 공공주택 특별법은 부동산 경로 없음(자산 기준이 따로) — FAIL, 그 밖의 국민주택은 경로 있음(제35조의3①2호)
        assertThat(req.check(over.realEstateValue(100_000_000L).build(), SpecialSupplyType.NEWBORN, PUBLIC).status())
                .isEqualTo(Status.FAIL);
        assertThat(req.check(over.realEstateValue(100_000_000L).build(), SpecialSupplyType.NEWBORN, NATIONAL).status())
                .isEqualTo(Status.PASS);
    }

    @Test
    @DisplayName("신생아 맞벌이 170% — 공공주택 특별법(공고 모름 포함)은 200% 라 PASS, 국민주택은 130% 초과 → 부동산 경로, 민영은 160% 초과 → 부동산 경로")
    void newbornByHousingRule() {
        ApplicantProfile dual170 = profile(12_240_000, 3, true);   // 약 170%
        assertThat(req.check(dual170, SpecialSupplyType.NEWBORN, PUBLIC).status()).isEqualTo(Status.PASS);
        assertThat(req.check(dual170, SpecialSupplyType.NEWBORN).status()).isEqualTo(Status.PASS);
        assertThat(req.check(dual170, SpecialSupplyType.NEWBORN, NATIONAL).status()).isEqualTo(Status.MISSING);
        assertThat(req.check(dual170, SpecialSupplyType.NEWBORN, PRIVATE).status()).isEqualTo(Status.MISSING);
    }

    @Test
    @DisplayName("공공주택 특별법 [별표 6] — 외벌이 상한(다자녀·노부모 120, 신혼·생애최초 130, 신생아 140), 맞벌이는 모두 200")
    void publicHousingAnnex6() {
        // 3인 7,198,649원 기준. 135% ≈ 9,718,176
        ApplicantProfile single135 = profile(9_718_176, 3, false);
        assertThat(req.check(single135, SpecialSupplyType.NEWLYWED, PUBLIC).status()).isEqualTo(Status.FAIL);    // 130 초과
        assertThat(req.check(single135, SpecialSupplyType.NEWBORN, PUBLIC).status()).isEqualTo(Status.PASS);     // 140 이내
        ApplicantProfile dual190 = profile(13_677_433, 3, true);   // 약 190%
        for (SpecialSupplyType t : java.util.List.of(SpecialSupplyType.MULTI_CHILD, SpecialSupplyType.NEWLYWED,
                SpecialSupplyType.FIRST_TIME, SpecialSupplyType.OLD_PARENTS, SpecialSupplyType.NEWBORN)) {
            assertThat(req.check(dual190, t, PUBLIC).status()).as(t.name()).isEqualTo(Status.PASS);
        }
    }

    @Test
    @DisplayName("공공주택 특별법 — 2023.3.28 이후 출생 자녀 가산: 그 자녀만 1명 +10%p, 2명 이상 또는 1명+그 전 자녀 +20%p. 민영·국민주택엔 없음")
    void birthBonus() {
        // 신혼 외벌이 130% → 138% 는 FAIL, 1명 가산(140)이면 PASS
        ApplicantProfile.ApplicantProfileBuilder p138 = ApplicantProfile.builder()
                .monthlyHouseholdIncome(9_934_136).householdSize(3).dualIncome(false);
        assertThat(req.check(p138.build(), SpecialSupplyType.NEWLYWED, PUBLIC).status()).isEqualTo(Status.FAIL);
        RequirementCheck one = req.check(p138.childCount(1).childrenBornSince20230328(1).build(), SpecialSupplyType.NEWLYWED, PUBLIC);
        assertThat(one.status()).isEqualTo(Status.PASS);
        assertThat(one.reason()).contains("140% 이하").contains("+10%p");
        // 145% — 1명이면 140 이라 FAIL, 1명 + 그 전 자녀면 150 이라 PASS
        ApplicantProfile.ApplicantProfileBuilder p145 = ApplicantProfile.builder()
                .monthlyHouseholdIncome(10_438_041).householdSize(3).dualIncome(false);
        assertThat(req.check(p145.childCount(1).childrenBornSince20230328(1).build(), SpecialSupplyType.NEWLYWED, PUBLIC).status())
                .isEqualTo(Status.FAIL);
        assertThat(req.check(p145.childCount(2).childrenBornSince20230328(1).build(), SpecialSupplyType.NEWLYWED, PUBLIC).status())
                .isEqualTo(Status.PASS);
        assertThat(req.check(p145.childCount(2).childrenBornSince20230328(2).build(), SpecialSupplyType.NEWLYWED, PUBLIC).reason())
                .contains("+20%p");
        // 국민주택(140)에는 가산이 없다 — 145% 는 부동산 경로로
        assertThat(req.check(p145.childCount(2).childrenBornSince20230328(2).build(), SpecialSupplyType.NEWLYWED, NATIONAL).status())
                .isEqualTo(Status.MISSING);
    }
}
