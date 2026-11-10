package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.rule.SpecialSupplyRequirementProperties.AmountVersion;
import com.portfolio.chungyak.rule.SpecialSupplyRequirementProperties.AssetAmounts;
import com.portfolio.chungyak.rule.SpecialSupplyRequirementProperties.AssetLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 매년 바뀌는 제도 금액을 공고일로 고른다 — 새 해 값을 넣어도 그 전에 나온 공고의 판정이 바뀌지 않아야 한다.
 */
class YearlyFigureTest {

    private static final LocalDate SWITCH = LocalDate.of(2027, 3, 1);
    private static final List<YearlyFigure<Long>> VERSIONS = List.of(
            new YearlyFigure<>(100L, "2026년도", null),
            new YearlyFigure<>(200L, "2027년도", SWITCH));

    @Test
    @DisplayName("공고일이 새 값 시작일 전이면 예전 값, 당일·이후면 새 값, 공고일을 모르면 최신")
    void picksByNoticeDate() {
        assertThat(YearlyFigure.pick(VERSIONS, SWITCH.minusDays(1)).value()).isEqualTo(100L);
        assertThat(YearlyFigure.pick(VERSIONS, SWITCH).value()).isEqualTo(200L);
        assertThat(YearlyFigure.pick(VERSIONS, SWITCH.plusYears(1)).basis()).isEqualTo("2027년도");
        assertThat(YearlyFigure.pick(VERSIONS, null).value()).isEqualTo(200L);
        assertThat(YearlyFigure.pick(VERSIONS, SWITCH.minusDays(1)).note()).isEmpty();
    }

    @Test
    @DisplayName("공고일이 알고 있는 모든 시작일보다 이르면 가장 이른 값을 쓰고 사유에 밝힌다")
    void beforeKnownRange() {
        List<YearlyFigure<Long>> dated = List.of(new YearlyFigure<>(200L, "2027년도", SWITCH));
        var pick = YearlyFigure.pick(dated, LocalDate.of(2026, 12, 1));

        assertThat(pick.value()).isEqualTo(200L);
        assertThat(pick.outsideKnownRange()).isTrue();
        assertThat(pick.note()).contains("2026-12-01").contains("2027년도").contains("공고문");
    }

    @Test
    @DisplayName("시작일 없는 버전이 둘이거나 시작일이 겹치면 설정 오류 — 어느 값을 쓸지 정할 수 없다")
    void rejectsAmbiguousVersions() {
        assertThatThrownBy(() -> YearlyFigure.of(new YearlyFigure<>(1L, "a", null), List.of(new YearlyFigure<>(2L, "b", null))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> YearlyFigure.of(new YearlyFigure<>(1L, "a", SWITCH), List.of(new YearlyFigure<>(2L, "b", SWITCH))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("소득표 — 공고일에 따라 다른 해 표로 환산하고 사유에 그 해를 적는다")
    void incomeTableByNoticeDate() {
        Map<Integer, Long> old = Map.of(3, 6_000_000L);
        Map<Integer, Long> now = Map.of(3, 7_500_000L);
        IncomeReference ref = new IncomeReference(new IncomeReferenceProperties("2025", now, SWITCH,
                List.of(new IncomeReferenceProperties.Version("2024", null, old))));
        IncomeRequirement income = new IncomeRequirement(ref, RuleTestSupport.REQUIREMENTS);
        ApplicantProfile p = ApplicantProfile.builder().monthlyHouseholdIncome(8_100_000).householdSize(3).build();

        // 공공주택 특별법 신혼부부 130%: 예전 표 기준 135% → 초과, 새 표 기준 108% → 충족
        RequirementCheck before = income.check(p, SpecialSupplyType.NEWLYWED, specialLaw(SWITCH.minusDays(1)));
        RequirementCheck after = income.check(p, SpecialSupplyType.NEWLYWED, specialLaw(SWITCH));
        assertThat(before.passed()).isFalse();
        assertThat(before.reason()).contains("2024년 기준").contains("135%");
        assertThat(after.passed()).isTrue();
        assertThat(after.reason()).contains("2025년 기준").contains("108%");
    }

    @Test
    @DisplayName("공공 특공 자산 기준 — 공고일에 맞는 해의 금액, 사유에 기준 연도")
    void assetLimitByNoticeDate() {
        AssetLimit old = new AssetLimit(200_000_000L, 40_000_000, null, null, "2026년도", null, null);
        AssetLimit now = new AssetLimit(250_000_000L, 50_000_000, null, null, "2027년도", SWITCH, List.of(old));
        AssetRequirement asset = new AssetRequirement(new SpecialSupplyRequirementProperties(Map.of(), now));
        ApplicantProfile p = ApplicantProfile.builder().realEstateValue(220_000_000L).carValue(10_000_000).build();

        RequirementCheck before = asset.check(p, publicAnn(SWITCH.minusDays(1)));
        RequirementCheck after = asset.check(p, publicAnn(SWITCH));
        assertThat(before.passed()).isFalse();
        assertThat(before.reason()).contains("2026년도 기준");
        assertThat(after.passed()).isTrue();
        assertThat(after.reason()).contains("2027년도 기준");
    }

    @Test
    @DisplayName("신혼희망타운 자산 — 공고일에 맞는 금액")
    void hopeTownAssetByNoticeDate() {
        NewlywedHopeTownProperties props = new NewlywedHopeTownProperties(400_000_000L, "2027년도", SWITCH,
                List.of(new AmountVersion(362_000_000L, "2026년도", null)));
        assertThat(props.assetLimitFor(SWITCH.minusDays(1)).value()).isEqualTo(362_000_000L);
        assertThat(props.assetLimitFor(SWITCH).value()).isEqualTo(400_000_000L);
    }

    @Test
    @DisplayName("application.yml 형식 그대로 바인딩된다 — applies-from·previous 포함")
    void bindsYamlShape() {
        MapConfigurationPropertySource src = new MapConfigurationPropertySource(Map.ofEntries(
                Map.entry("income-reference.basis-year", "2026"),
                Map.entry("income-reference.applies-from", "2027-03-01"),
                Map.entry("income-reference.monthly-average.3", "8000000"),
                Map.entry("income-reference.previous[0].basis-year", "2025"),
                Map.entry("income-reference.previous[0].monthly-average.3", "7533763"),
                Map.entry("special-supply.asset-limit.real-estate", "230000000"),
                Map.entry("special-supply.asset-limit.car-value", "46000000"),
                Map.entry("special-supply.asset-limit.basis", "2027년도"),
                Map.entry("special-supply.asset-limit.applies-from", "2027-01-01"),
                Map.entry("special-supply.asset-limit.previous[0].real-estate", "215500000"),
                Map.entry("special-supply.asset-limit.previous[0].car-value", "45420000"),
                Map.entry("special-supply.asset-limit.previous[0].basis", "2026년도"),
                Map.entry("special-supply.real-estate-limit", "340000000"),
                Map.entry("special-supply.real-estate-limit-basis", "2027년"),
                Map.entry("special-supply.real-estate-limit-applies-from", "2027-01-01"),
                Map.entry("special-supply.real-estate-limit-previous[0].amount", "331000000"),
                Map.entry("special-supply.real-estate-limit-previous[0].basis", "2026년"),
                Map.entry("newlywed-hope-town.asset-limit", "370000000"),
                Map.entry("newlywed-hope-town.basis", "2027년도"),
                Map.entry("newlywed-hope-town.applies-from", "2027-01-01"),
                Map.entry("newlywed-hope-town.previous[0].amount", "362000000"),
                Map.entry("newlywed-hope-town.previous[0].basis", "2026년도")));
        Binder binder = new Binder(src);

        IncomeReference ref = new IncomeReference(binder.bind("income-reference", IncomeReferenceProperties.class).get());
        var special = binder.bind("special-supply", SpecialSupplyRequirementProperties.class).get();
        var hope = binder.bind("newlywed-hope-town", NewlywedHopeTownProperties.class).get();
        LocalDate before = LocalDate.of(2026, 12, 1);

        assertThat(ref.tableFor(before).basis()).isEqualTo("2025");
        assertThat(ref.tableFor(before).value().get(3)).isEqualTo(7_533_763L);
        assertThat(special.assetLimitFor(before).value().realEstate()).isEqualTo(215_500_000L);
        assertThat(special.assetLimitFor(LocalDate.of(2027, 2, 1)).basis()).isEqualTo("2027년도");
        assertThat(special.realEstateLimitFor(before).value()).isEqualTo(331_000_000L);
        assertThat(hope.assetLimitFor(before).value()).isEqualTo(362_000_000L);
    }

    private static Announcement specialLaw(LocalDate noticeDate) {
        return Announcement.builder()
                .externalId("A").houseManageNo("A").pblancNo("A").houseName("t")
                .houseType(HouseType.APT).houseDetailType(HouseDetailType.PUBLIC)
                .regulationFlags(com.portfolio.chungyak.domain.RegulationFlags.builder().publicHousingSpecialLaw(true).build())
                .noticeDate(noticeDate).build();
    }

    private static Announcement publicAnn(LocalDate noticeDate) {
        return Announcement.builder()
                .externalId("A").houseManageNo("A").pblancNo("A").houseName("t")
                .houseType(HouseType.APT).houseDetailType(HouseDetailType.PUBLIC)
                .noticeDate(noticeDate).build();
    }
}
