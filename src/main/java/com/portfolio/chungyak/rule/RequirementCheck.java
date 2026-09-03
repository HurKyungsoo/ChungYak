package com.portfolio.chungyak.rule;

/**
 * 규칙 안에서 공통으로 확인하는 요건(소득·자산 등) 하나의 결과.
 *
 * 규칙은 이 결과를 자기 판정에 접어 넣는다:
 *  - {@code passed()} 를 자기 pass 조건에 AND
 *  - {@link #describe} 로 satisfied/failed/missing 사유를 결정에 추가
 */
public record RequirementCheck(Status status, String reason) {

    public enum Status { PASS, FAIL, MISSING }

    public static RequirementCheck pass(String reason) {
        return new RequirementCheck(Status.PASS, reason);
    }

    public static RequirementCheck fail(String reason) {
        return new RequirementCheck(Status.FAIL, reason);
    }

    /** reason 은 빠진 입력 항목 이름 */
    public static RequirementCheck missing(String input) {
        return new RequirementCheck(Status.MISSING, input);
    }

    /** PASS 여야 규칙 통과. MISSING 은 "판정 불가"라 통과가 아니다. */
    public boolean passed() {
        return status == Status.PASS;
    }

    public void describe(EligibilityDecision decision) {
        switch (status) {
            case PASS -> decision.satisfied(reason);
            case FAIL -> decision.failed(reason);
            case MISSING -> decision.missing(reason);
        }
    }
}
