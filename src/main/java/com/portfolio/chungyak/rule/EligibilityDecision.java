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

    /**
     * {@link CommonRequirements#checkAll} 의 결과를 재당첨·소득·자산·통장·거주 순서 그대로 보관한다
     * — 화면이 여러 유형에 걸쳐 같은 요건을 중복 표시하지 않고 한 번에 보여줄 때 쓴다.
     * {@link CommonRequirements} 를 쓰지 않는 규칙(예: 신혼희망타운)은 비어 있다.
     * 판정 로직에는 관여하지 않는다 — satisfied/failed/missing 은 이것과 별개로 그대로 쌓인다.
     */
    private List<RequirementCheck> commonChecks = List.of();

    /** {@link #failed} 로 남은(메울 수 없는) 미충족 수 — {@link #isFixable()} 판단용 */
    private int unfixableFailures = 0;

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

    /**
     * 메울 수 없는 미충족 — 혼인 7년 초과, 주택 소유, 소득·자산 초과처럼 기간이 지나도 채워지지 않는 것.
     * 기간·선택으로 메울 수 있는 미충족은 {@link #failedWithHint} 로 남긴다.
     */
    public EligibilityDecision failed(String reason) {
        this.failedReasons.add(reason);
        this.unfixableFailures++;
        return this;
    }

    /**
     * 기간이 지나거나 선택으로 메울 수 있는 미충족 + 그 방법. 힌트가 null 이면 메울 수 없는 것으로 본다.
     * {@link #isFixable()} 은 모든 미충족이 이것으로만 남았을 때 참이다.
     */
    public EligibilityDecision failedWithHint(String reason, String improvementHint) {
        if (improvementHint == null) return failed(reason);
        this.failedReasons.add(reason);
        this.improvementHints.add(improvementHint);
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

    /** {@link CommonRequirements#describeAll} 이 호출한다. {@link #commonChecks} 참고. */
    public EligibilityDecision commonChecks(List<RequirementCheck> checks) {
        this.commonChecks = checks;
        return this;
    }

    /** 입력이 부족해 판정을 못 내린 상태인지 */
    public boolean isUndetermined() {
        return !missingInputs.isEmpty();
    }

    /**
     * "지금은 안 되지만 이것만 바뀌면 된다" — 미충족이 있고, 그 미충족이 <b>전부</b> 기간·선택으로
     * 메울 수 있는 것({@link #failedWithHint})일 때만 참이다. 하나라도 메울 수 없는 미충족(혼인 7년 초과 등)이
     * 섞이면 거짓 — 통장만 채워도 여전히 불가인 경우를 "가능"으로 읽히게 하지 않기 위해서다.
     * 입력이 부족한(판정 보류) 상태는 메울 수 있는지 알 수 없으므로 거짓이다.
     */
    public boolean isFixable() {
        return !eligible && !isUndetermined() && !failedReasons.isEmpty() && unfixableFailures == 0;
    }
}
