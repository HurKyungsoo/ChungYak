package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 한 특별공급 유형에 대한 판정 결과.
 *
 * 결과는 결정론적이다 — 같은 입력이면 항상 같은 출력.
 * LLM 은 이 객체를 자연어로 풀어 설명할 뿐, 판정 자체에 관여하지 않는다.
 *
 * 이유(reasons)를 반드시 남기는 게 핵심이다. "안 됩니다"만 주면
 * 사용자는 무엇을 고쳐야 할지 모르고, LLM 도 설명할 근거가 없어
 * 그럴듯한 문장을 지어내게 된다.
 */
@Getter
public class EligibilityDecision {

    private final SpecialSupplyType type;
    private final boolean eligible;
    private final List<String> satisfiedReasons = new ArrayList<>();
    private final List<String> failedReasons = new ArrayList<>();

    /** 판정 불가 — 사용자 입력이 부족한 경우 */
    private final List<String> missingInputs = new ArrayList<>();

    /**
     * "이렇게 하면 자격이 생긴다" — 미충족 요건의 수치 차이로 규칙이 <b>결정론적으로</b> 계산한 안내.
     * 예: "청약통장을 12개월 더 유지하면 요건(24개월)을 충족합니다."
     * 시간이 지나거나 선택으로 메울 수 있는 격차(통장 기간·거주 기간·재당첨 제한·예치금·납입 횟수)만 담는다.
     * 혼인 기간 초과처럼 되돌릴 수 없는 격차나 소득·자산 초과는 담지 않는다.
     * LLM 은 이 문장을 자연스럽게 녹여 설명할 뿐, 새 수치를 만들지 않는다.
     */
    private final List<String> improvementHints = new ArrayList<>();

    private EligibilityDecision(SpecialSupplyType type, boolean eligible) {
        this.type = type;
        this.eligible = eligible;
    }

    public static EligibilityDecision eligible(SpecialSupplyType type) {
        return new EligibilityDecision(type, true);
    }

    public static EligibilityDecision ineligible(SpecialSupplyType type) {
        return new EligibilityDecision(type, false);
    }

    public EligibilityDecision satisfied(String reason) {
        this.satisfiedReasons.add(reason);
        return this;
    }

    public EligibilityDecision failed(String reason) {
        this.failedReasons.add(reason);
        return this;
    }

    public EligibilityDecision missing(String input) {
        this.missingInputs.add(input);
        return this;
    }

    /** 미충족 요건을 메우는 결정론적 안내 한 줄. {@link #improvementHints} 참고. */
    public EligibilityDecision hint(String improvementHint) {
        this.improvementHints.add(improvementHint);
        return this;
    }

    /** 입력이 부족해 판정을 못 내린 상태인지 */
    public boolean isUndetermined() {
        return !missingInputs.isEmpty();
    }
}
