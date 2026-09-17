package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.RegulationFlags;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * describeAll 이 사유뿐 아니라 화면용 commonChecks(재당첨·소득·자산·통장·거주 순서)도
 * 결정에 남기는지 확인한다. 판정(satisfied/failed/missing) 자체는 각 요건 테스트가 이미 본다.
 */
class CommonRequirementsTest {

    private final CommonRequirements common = RuleTestSupport.COMMON;

    private static Announcement announcement(boolean regulated) {
        return Announcement.builder()
                .externalId("A").houseManageNo("A").pblancNo("A").houseName("t")
                .houseType(HouseType.APT).houseDetailType(HouseDetailType.PRIVATE).regionName("서울")
                .regulationFlags(RegulationFlags.builder().speculationOverheated(regulated).build())
                .build();
    }

    @Test
    @DisplayName("commonChecks 는 재당첨·소득·자산·통장·거주 순서 그대로 결정에 남는다")
    void commonChecksKeepDeclaredOrder() {
        ApplicantProfile profile = RuleTestSupport.passingIncomeAndAssets().build();
        Announcement a = announcement(false);

        List<RequirementCheck> checks = common.checkAll(profile, a, SpecialSupplyType.NEWLYWED);
        EligibilityDecision decision = EligibilityDecision.eligible(SpecialSupplyType.NEWLYWED);
        CommonRequirements.describeAll(checks, decision);

        assertThat(decision.getCommonChecks()).hasSize(5);
        assertThat(CommonRequirements.KIND_ORDER)
                .containsExactly(RequirementKind.RE_WIN, RequirementKind.INCOME, RequirementKind.ASSET,
                        RequirementKind.ACCOUNT, RequirementKind.RESIDENCE);
        // 순서가 KIND_ORDER 와 어긋나면 화면이 엉뚱한 라벨을 붙이게 된다 —
        // checkAll() 이 반환하는 리스트 순서가 KIND_ORDER 와 같은 순서라는 계약을 고정한다.
        assertThat(decision.getCommonChecks()).isEqualTo(checks);
    }

    @Test
    @DisplayName("describeAll 은 여전히 satisfied/failed/missing 사유도 그대로 남긴다")
    void describeAllStillFillsReasons() {
        // 소득 요건만 초과하도록 만든 프로필 — 신혼부부 소득상한 140%를 넘긴다
        ApplicantProfile profile = RuleTestSupport.passingIncomeAndAssets()
                .monthlyHouseholdIncome(20_000_000)
                .build();
        Announcement a = announcement(false);

        List<RequirementCheck> checks = common.checkAll(profile, a, SpecialSupplyType.NEWLYWED);
        EligibilityDecision decision = EligibilityDecision.ineligible(SpecialSupplyType.NEWLYWED);
        CommonRequirements.describeAll(checks, decision);

        assertThat(decision.getFailedReasons()).anyMatch(r -> r.contains("소득"));
        assertThat(decision.getCommonChecks()).extracting(RequirementCheck::status)
                .contains(RequirementCheck.Status.FAIL);
    }
}
