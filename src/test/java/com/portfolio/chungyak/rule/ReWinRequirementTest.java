package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.rule.RequirementCheck.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재당첨 제한 확인 (평생 1회 + 기간제).
 */
class ReWinRequirementTest {

    private final ReWinRequirement req = new ReWinRequirement(new ReWinRestrictionProperties(120, 60));

    private static ApplicantProfile profile(boolean wonSpecial, Integer monthsSinceWin, boolean speculation) {
        return ApplicantProfile.builder()
                .everWonSpecialSupply(wonSpecial)
                .monthsSinceLastWin(monthsSinceWin)
                .pastWinInSpeculationArea(speculation)
                .build();
    }

    @Test
    @DisplayName("과거 특별공급 당첨 이력이 있으면 무조건 FAIL (평생 1회)")
    void pastSpecialSupplyWinIsHardFail() {
        RequirementCheck c = req.check(profile(true, 300, false), SpecialSupplyType.NEWLYWED);
        assertThat(c.status()).isEqualTo(Status.FAIL);
        assertThat(c.reason()).contains("평생 1회");
    }

    @Test
    @DisplayName("당첨 이력이 없으면 PASS")
    void noWinHistoryPasses() {
        assertThat(req.check(profile(false, null, false), SpecialSupplyType.NEWLYWED).status())
                .isEqualTo(Status.PASS);
    }

    @Test
    @DisplayName("투기과열지구 당첨이었다고 체크했는데 경과 개월이 비어 있으면 " +
            "'이력 없음'으로 단정하지 않고 MISSING (체크 자체가 이력이 있다는 뜻이므로)")
    void speculationCheckedWithoutMonthsIsMissingNotPass() {
        RequirementCheck c = req.check(profile(false, null, true), SpecialSupplyType.NEWLYWED);
        assertThat(c.status()).isEqualTo(Status.MISSING);
        assertThat(c.reason()).contains("경과 개월");
    }

    @Test
    @DisplayName("일반 지역 당첨 — 60개월 경계")
    void defaultPeriodBoundary() {
        assertThat(req.check(profile(false, 59, false), SpecialSupplyType.FIRST_TIME).passed()).isFalse();
        assertThat(req.check(profile(false, 60, false), SpecialSupplyType.FIRST_TIME).passed()).isTrue();
    }

    @Test
    @DisplayName("투기과열지구 당첨 — 120개월 경계 (더 길다)")
    void speculationAreaPeriodBoundary() {
        assertThat(req.check(profile(false, 119, true), SpecialSupplyType.FIRST_TIME).passed()).isFalse();
        assertThat(req.check(profile(false, 120, true), SpecialSupplyType.FIRST_TIME).passed()).isTrue();
        // 같은 경과 개월(100)도 지역에 따라 갈린다
        assertThat(req.check(profile(false, 100, false), SpecialSupplyType.FIRST_TIME).passed()).isTrue();
        assertThat(req.check(profile(false, 100, true), SpecialSupplyType.FIRST_TIME).passed()).isFalse();
    }

    @Test
    @DisplayName("FAIL 사유에 경과 개월과 제한 기간이 들어간다")
    void reasonCarriesNumbers() {
        RequirementCheck c = req.check(profile(false, 30, false), SpecialSupplyType.NEWLYWED);
        assertThat(c.reason()).contains("30개월").contains("60개월");
    }

    @Test
    @DisplayName("기간 미경과면 '몇 개월 더 지나면 풀린다' 개선 안내가 붙는다")
    void failCarriesImprovementHint() {
        RequirementCheck c = req.check(profile(false, 48, false), SpecialSupplyType.NEWLYWED);  // 60개월에 12개월 부족
        assertThat(c.improvementHint()).contains("12개월").contains("풀립니다");
    }

    @Test
    @DisplayName("평생 1회 하드 FAIL 에는 개선 안내가 없다 (되돌릴 수 없음)")
    void lifetimeFailHasNoHint() {
        assertThat(req.check(profile(true, 300, false), SpecialSupplyType.NEWLYWED).improvementHint())
                .isNull();
    }
}
