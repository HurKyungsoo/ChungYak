package com.portfolio.chungyak.rule;

/**
 * {@link CommonRequirements} 가 확인하는 요건의 종류.
 *
 * 여러 특별공급 유형이 같은 요건(예: 소득)을 각자 판정 결과에 반복해서 담기 때문에,
 * 화면에서 유형을 넘나들며 같은 요건을 한데 모아 보여줄 때 이 태그로 구분한다.
 * 판정 로직에는 관여하지 않는다 — {@link EligibilityDecision#commonChecks} 에만 쓰인다.
 */
public enum RequirementKind {
    RE_WIN("재당첨 제한"),
    INCOME("소득"),
    ASSET("자산"),
    ACCOUNT("청약통장"),
    RESIDENCE("거주");

    private final String label;

    RequirementKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
