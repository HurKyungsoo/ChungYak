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
    void allTypesAgreeing_producesSingleSharedStatusAndKeepsTheSentence() {
        List<CommonCheckView> checks = List.of(
                new CommonCheckView("재당첨 제한", "PASS", "r1", null),
                new CommonCheckView("소득", "PASS", "r2", null));

        CommonRequirementRail rail = CommonRequirementRail.of(List.of(
                decision(checks).toView(), decision(checks).toView()));

        assertThat(rail.present()).isTrue();
        assertThat(rail.steps()).hasSize(2);
        assertThat(rail.steps()).allSatisfy(s -> assertThat(s.pass()).isTrue());
        assertThat(rail.steps().get(1).reason()).isEqualTo("r2");
    }

    @Test
    void differingStatusAcrossTypes_isMarkedMixedWithAPlaceholderReason() {
        List<CommonCheckView> incomePasses = List.of(
                new CommonCheckView("소득", "PASS", "70%로 요건(160%) 충족", null));
        List<CommonCheckView> incomeFails = List.of(
                new CommonCheckView("소득", "FAIL", "70%로 요건(60%) 초과", null));

        CommonRequirementRail rail = CommonRequirementRail.of(List.of(
                decision(incomePasses).toView(), decision(incomeFails).toView()));

        assertThat(rail.steps()).hasSize(1);
        assertThat(rail.steps().get(0).mixed()).isTrue();
        // 유형마다 결과가 다르니 어느 한쪽 문장만 대표로 보여주면 안 된다 — 새 수치를 지어내지 않고
        // "유형별로 다르다"는 사실만 안내한다.
        assertThat(rail.steps().get(0).reason())
                .doesNotContain("70%").contains("유형마다");
    }

    @Test
    void noDecisionHasCommonChecks_railIsAbsent() {
        CommonRequirementRail rail = CommonRequirementRail.of(List.of(decision(List.of()).toView()));

        assertThat(rail.present()).isFalse();
        assertThat(rail.steps()).isEmpty();
    }
}
