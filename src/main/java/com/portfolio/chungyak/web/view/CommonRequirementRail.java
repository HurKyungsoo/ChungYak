package com.portfolio.chungyak.web.view;

import java.util.ArrayList;
import java.util.List;

/**
 * 여러 특별공급 유형이 공유하는 공통요건(재당첨·소득·자산·통장·거주)을 한 번에 보여주는 요약 레일.
 *
 * 유형마다 결과가 같으면 하나로 합쳐 보여준다. 소득처럼 유형별 상한이 달라 결과가 갈리면
 * (한 유형은 통과, 다른 유형은 미달) "유형별로 다름"(MIXED)으로 표시하고, 정확한 내용은
 * 각 유형 카드에서 확인하게 한다 — 다른 결과를 같다고 뭉뚱그리지 않는다.
 */
public record CommonRequirementRail(List<Step> steps) {

    public record Step(String label, String status) {
        public boolean pass() { return "PASS".equals(status); }
        public boolean fail() { return "FAIL".equals(status); }
        public boolean missing() { return "MISSING".equals(status); }
        public boolean mixed() { return "MIXED".equals(status); }
    }

    public static CommonRequirementRail of(List<EligibilityResultView.TypeDecision> decisions) {
        List<List<CommonCheckView>> withChecks = decisions.stream()
                .map(EligibilityResultView.TypeDecision::commonChecks)
                .filter(checks -> !checks.isEmpty())
                .toList();
        if (withChecks.isEmpty()) {
            return new CommonRequirementRail(List.of());
        }

        List<Step> steps = new ArrayList<>();
        int size = withChecks.get(0).size();
        for (int i = 0; i < size; i++) {
            int idx = i;
            String label = withChecks.get(0).get(idx).kindLabel();
            String firstStatus = withChecks.get(0).get(idx).status();
            boolean allSame = withChecks.stream().allMatch(checks -> checks.get(idx).status().equals(firstStatus));
            steps.add(new Step(label, allSame ? firstStatus : "MIXED"));
        }
        return new CommonRequirementRail(steps);
    }

    public boolean present() {
        return !steps.isEmpty();
    }
}
