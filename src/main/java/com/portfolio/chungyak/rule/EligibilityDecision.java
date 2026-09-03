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

    /** 입력이 부족해 판정을 못 내린 상태인지 */
    public boolean isUndetermined() {
        return !missingInputs.isEmpty();
    }
}
