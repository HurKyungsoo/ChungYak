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
        // 3인 도시근로자 7,198,649원. 신혼 상한 140% = 10,078,108원.
        assertThat(req.check(profile(7_000_000, 3, false), SpecialSupplyType.NEWLYWED).status())
                .isEqualTo(Status.PASS);
        assertThat(req.check(profile(11_000_000, 3, false), SpecialSupplyType.NEWLYWED).status())
                .isEqualTo(Status.FAIL);
    }

    @Test
    @DisplayName("맞벌이는 완화된 상한을 쓴다")
    void dualIncomeUsesRelaxedLimit() {
        // 약 146% — 단독 140 초과, 맞벌이 160 이내
        assertThat(req.check(profile(10_500_000, 3, false), SpecialSupplyType.NEWLYWED).passed()).isFalse();
        assertThat(req.check(profile(10_500_000, 3, true), SpecialSupplyType.NEWLYWED).passed()).isTrue();
    }

    @Test
    @DisplayName("다자녀·노부모는 맞벌이 완화가 없다 (single = dual = 120)")
    void multiChildNoDualRelaxation() {
        // 약 121% — 120 초과. 맞벌이여도 그대로 FAIL
        assertThat(req.check(profile(8_700_000, 3, true), SpecialSupplyType.MULTI_CHILD).passed()).isFalse();
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
        RequirementCheck c = req.check(profile(7_000_000, 3, false), SpecialSupplyType.NEWLYWED);
        assertThat(c.reason()).contains("%").contains("140% 이하");
    }
}
