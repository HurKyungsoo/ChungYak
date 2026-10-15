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
    @DisplayName("과거 특별공급 당첨 이력이 있으면 FAIL (한 차례 한정, 제55조) — 특례 입력이 없을 때")
    void pastSpecialSupplyWinIsHardFail() {
        RequirementCheck c = req.check(profile(true, 300, false), SpecialSupplyType.NEWLYWED);
        assertThat(c.status()).isEqualTo(Status.FAIL);
        assertThat(c.reason()).contains("한 차례 한정");
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

    // ── 제55조의3 특례 (2025. 3. 31. 개정, 2026. 6. 15. 현행 조문 기준) ──

    private static ApplicantProfile won(SpecialSupplyWinTiming timing, boolean childSince20240619) {
        return ApplicantProfile.builder().everWonSpecialSupply(true)
                .specialSupplyWinTiming(timing).hasChildBornSince20240619(childSince20240619).build();
    }

    @Test
    @DisplayName("① 배우자의 혼인 전 당첨 — 신생아·신혼부부·생애최초는 PASS, 다자녀·노부모는 FAIL")
    void spousePreMarriageWin() {
        ApplicantProfile p = won(SpecialSupplyWinTiming.SPOUSE_BEFORE_MARRIAGE, false);
        for (SpecialSupplyType t : java.util.List.of(SpecialSupplyType.NEWBORN, SpecialSupplyType.NEWLYWED, SpecialSupplyType.FIRST_TIME)) {
            RequirementCheck c = req.check(p, t);
            assertThat(c.status()).as(t.name()).isEqualTo(Status.PASS);
            assertThat(c.reason()).contains("제55조의3제1항");
        }
        assertThat(req.check(p, SpecialSupplyType.MULTI_CHILD).status()).isEqualTo(Status.FAIL);
        assertThat(req.check(p, SpecialSupplyType.OLD_PARENTS).status()).isEqualTo(Status.FAIL);
    }

    @Test
    @DisplayName("② 본인의 혼인 전 당첨 — 신혼부부만 한 차례 PASS, 생애최초·신생아는 FAIL")
    void selfPreMarriageWin() {
        ApplicantProfile p = won(SpecialSupplyWinTiming.SELF_BEFORE_MARRIAGE, false);
        RequirementCheck c = req.check(p, SpecialSupplyType.NEWLYWED);
        assertThat(c.status()).isEqualTo(Status.PASS);
        assertThat(c.reason()).contains("제55조의3제2항").contains("한 차례");
        assertThat(req.check(p, SpecialSupplyType.FIRST_TIME).status()).isEqualTo(Status.FAIL);
        assertThat(req.check(p, SpecialSupplyType.NEWBORN).status()).isEqualTo(Status.FAIL);
    }

    @Test
    @DisplayName("③ 2024.6.19 이후 출생 자녀 — 혼인 후 이력이어도 신생아·다자녀·신혼부부·노부모 PASS, 생애최초는 FAIL")
    void childBornSince20240619() {
        ApplicantProfile p = won(SpecialSupplyWinTiming.OTHER, true);
        for (SpecialSupplyType t : java.util.List.of(SpecialSupplyType.NEWBORN, SpecialSupplyType.MULTI_CHILD,
                SpecialSupplyType.NEWLYWED, SpecialSupplyType.OLD_PARENTS)) {
            RequirementCheck c = req.check(p, t);
            assertThat(c.status()).as(t.name()).isEqualTo(Status.PASS);
            assertThat(c.reason()).contains("제55조의3제3항").contains("이 판정에는 반영하지 않았습니다");
        }
        assertThat(req.check(p, SpecialSupplyType.FIRST_TIME).status()).isEqualTo(Status.FAIL);
        assertThat(req.check(p, SpecialSupplyType.NEWLYWED_HOPE_TOWN).status()).isEqualTo(Status.FAIL);
    }

    @Test
    @DisplayName("특례가 있어도 재당첨 제한 기간은 따로 본다 — 기간 미달이면 FAIL")
    void exemptionStillChecksPeriod() {
        ApplicantProfile p = ApplicantProfile.builder().everWonSpecialSupply(true)
                .specialSupplyWinTiming(SpecialSupplyWinTiming.SPOUSE_BEFORE_MARRIAGE)
                .monthsSinceLastWin(59).build();
        assertThat(req.check(p, SpecialSupplyType.NEWLYWED).status()).isEqualTo(Status.FAIL);
        ApplicantProfile after = ApplicantProfile.builder().everWonSpecialSupply(true)
                .specialSupplyWinTiming(SpecialSupplyWinTiming.SPOUSE_BEFORE_MARRIAGE)
                .monthsSinceLastWin(60).build();
        assertThat(req.check(after, SpecialSupplyType.NEWLYWED).reason()).contains("제55조의3제1항").contains("60개월");
    }

    @Test
    @DisplayName("특례 입력이 없으면 FAIL 사유에 어떤 입력이면 달라지는지 알려 준다(유형별로 해당하는 것만)")
    void failHintsAtExceptions() {
        ApplicantProfile p = won(SpecialSupplyWinTiming.OTHER, false);
        assertThat(req.check(p, SpecialSupplyType.NEWLYWED).reason())
                .contains("본인·배우자의 혼인신고 전 당첨이거나 2024년 6월 19일 이후 출생한 자녀가 있으면");
        assertThat(req.check(p, SpecialSupplyType.FIRST_TIME).reason())
                .contains("배우자의 혼인신고 전 당첨이면").doesNotContain("2024년 6월 19일");
        assertThat(req.check(p, SpecialSupplyType.MULTI_CHILD).reason())
                .contains("2024년 6월 19일 이후 출생한 자녀가 있으면").doesNotContain("혼인신고 전");
        assertThat(req.check(p, SpecialSupplyType.NEWLYWED_HOPE_TOWN).reason()).doesNotContain("다만");
    }
}
