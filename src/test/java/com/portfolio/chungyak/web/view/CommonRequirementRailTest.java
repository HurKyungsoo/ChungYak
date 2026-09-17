package com.portfolio.chungyak.web.view;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommonRequirementRailTest {

    private TypeDecisionStub decision(List<CommonCheckView> checks) {
        return new TypeDecisionStub(checks);
    }

    /** 실제 레코드 생성자가 길어서, 이 테스트에 필요한 값만 넘기는 얇은 헬퍼. */
    private record TypeDecisionStub(List<CommonCheckView> checks) {
        EligibilityResultView.TypeDecision toView() {
            return new EligibilityResultView.TypeDecision(
                    "테스트유형", true, false, List.of(), List.of(), List.of(), List.of(),
                    checks, List.of(), List.of(), List.of(), List.of());
        }
    }

    @Test
    void allTypesAgreeing_producesSingleSharedStatus() {
        List<CommonCheckView> checks = List.of(
                new CommonCheckView("재당첨 제한", "PASS", "r1", null),
                new CommonCheckView("소득", "PASS", "r2", null));

        CommonRequirementRail rail = CommonRequirementRail.of(List.of(
                decision(checks).toView(), decision(checks).toView()));

        assertThat(rail.present()).isTrue();
        assertThat(rail.steps()).hasSize(2);
        assertThat(rail.steps()).allSatisfy(s -> assertThat(s.pass()).isTrue());
    }

    @Test
    void differingStatusAcrossTypes_isMarkedMixed() {
        List<CommonCheckView> incomePasses = List.of(
                new CommonCheckView("소득", "PASS", "70%로 요건(160%) 충족", null));
        List<CommonCheckView> incomeFails = List.of(
                new CommonCheckView("소득", "FAIL", "70%로 요건(60%) 초과", null));

        CommonRequirementRail rail = CommonRequirementRail.of(List.of(
                decision(incomePasses).toView(), decision(incomeFails).toView()));

        assertThat(rail.steps()).hasSize(1);
        assertThat(rail.steps().get(0).mixed()).isTrue();
    }

    @Test
    void noDecisionHasCommonChecks_railIsAbsent() {
        CommonRequirementRail rail = CommonRequirementRail.of(List.of(decision(List.of()).toView()));

        assertThat(rail.present()).isFalse();
        assertThat(rail.steps()).isEmpty();
    }
}
