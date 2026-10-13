package com.portfolio.chungyak.llm;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.rule.EligibilityDecision;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import com.portfolio.chungyak.rule.EligibilityEngine.UnitMatch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * MatchResult -> LLM 에게 줄 "확정된 근거" 텍스트. <b>최소화된 입력</b>이다 (#20).
 *
 * 순수 함수. 여기서 판정을 다시 하지 않는다 — 이미 결정된 값을 옮겨 담기만 한다.
 *
 * <b>무엇을 넘기고 무엇을 빼는가.</b> 결론(신청 가능 유형·세대수·물량 없음)은 화면 맨 위 요약 카드가
 * 코드로 이미 말한다. LLM 이 맡는 건 그 아래 "다음에 할 일" 한두 문장뿐이라, 그에 필요한 것만 넘긴다:
 * <ul>
 *   <li>넘김 — 유형별 판정 분류, 공고의 유형별 배정 세대(공개 데이터), 엔진이 계산한 보완 힌트(개월·횟수),
 *       판정 보류 유형이 요구하는 입력 이름</li>
 *   <li>뺌 — 충족·미충족 사유 문장 전부. 사유에는 사용자의 소득(도시근로자 대비 %)·총자산·자동차가액·
 *       통장 가입기간 같은 입력값이 그대로 들어 있다. 힌트 안의 금액(예치금 부족분 → 현재 예치금이 역산된다)도
 *       {@link #MONEY} 로 가린다.</li>
 * </ul>
 * 소득·자산·예치금 금액이 이 텍스트에 없다는 것은 {@code ExplanationFactsTest} 가 고정한다.
 */
final class ExplanationFacts {

    private ExplanationFacts() {}

    /** {@code AssetRequirement.won} 형식 — "1억 2,000만원", "300만원", "5,000원", "1억원" */
    static final Pattern MONEY = Pattern.compile("\\d[\\d,]*억(?:\\s*\\d[\\d,]*만)?원|\\d[\\d,]*만원|\\d[\\d,]*원");

    static final String MONEY_MASK = "[금액]";

    static String format(MatchResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("아래는 규칙 엔진이 이미 확정한 판정이다. 결론은 화면에 이미 표시돼 있다.\n\n");
        sb.append("공고: ").append(result.announcement().getHouseName()).append('\n');

        Map<SpecialSupplyType, Integer> applicable = applicable(result);
        Set<SpecialSupplyType> fixable = new LinkedHashSet<>();
        Set<SpecialSupplyType> blocked = new LinkedHashSet<>();
        Set<SpecialSupplyType> undetermined = new LinkedHashSet<>();
        for (var e : result.decisions().entrySet()) {
            EligibilityDecision d = e.getValue();
            if (d.isEligible()) continue;
            if (d.isUndetermined()) undetermined.add(e.getKey());
            else if (d.isFixable()) fixable.add(e.getKey());
            else blocked.add(e.getKey());
        }

        sb.append("\n판정 (확정됨):\n");
        sb.append("- 신청 가능: ");
        if (applicable.isEmpty()) sb.append("없음");
        else {
            List<String> parts = new ArrayList<>();
            applicable.forEach((t, n) -> parts.add(t.getLabel() + " " + n + "세대"));
            sb.append(String.join(", ", parts));
        }
        sb.append('\n');
        line(sb, "자격은 되지만 이 공고에 물량 없음", result.qualifiedButUnavailable());
        line(sb, "보완하면 가능", fixable);
        line(sb, "지금 조건으로는 어려움", blocked);
        line(sb, "입력이 부족해 판정 보류", undetermined);

        // 보완 힌트 — 한 줄에 하나(같은 줄 숫자끼리만 차이를 허용하는 ContradictionCheck 와 맞춘다)
        boolean anyHint = false;
        for (SpecialSupplyType t : concat(fixable, blocked)) {
            EligibilityDecision d = result.decisions().get(t);
            if (d.getImprovementHints().isEmpty()) continue;
            if (!anyHint) {
                sb.append("\n엔진이 계산한 보완 방법:\n");
                anyHint = true;
            }
            String tag = fixable.contains(t) ? "" : " (이것만으로는 여전히 신청 불가 — 다른 미충족 요건이 있음)";
            for (String h : new LinkedHashSet<>(d.getImprovementHints())) {
                sb.append("- [").append(t.getLabel()).append("]").append(tag).append(' ')
                        .append(maskMoney(h)).append('\n');
            }
        }

        if (!undetermined.isEmpty()) {
            sb.append("\n판정에 더 필요한 입력:\n");
            for (SpecialSupplyType t : undetermined) {
                sb.append("- [").append(t.getLabel()).append("] ")
                        .append(String.join(", ", result.decisions().get(t).getMissingInputs())).append('\n');
            }
        }
        return sb.toString();
    }

    static String maskMoney(String text) {
        return MONEY.matcher(text).replaceAll(MONEY_MASK);
    }

    /** 주택형마다 갈린 배정 세대를 유형별로 합산 — 공고의 공개 데이터다 */
    private static Map<SpecialSupplyType, Integer> applicable(MatchResult result) {
        Map<SpecialSupplyType, Integer> merged = new LinkedHashMap<>();
        for (UnitMatch m : result.matches()) {
            for (SpecialSupplyType type : m.applicableTypes()) {
                merged.merge(type, m.unitType().getSupplyBreakdown().countOf(type), Integer::sum);
            }
        }
        return merged;
    }

    private static void line(StringBuilder sb, String head, java.util.Collection<SpecialSupplyType> types) {
        if (types.isEmpty()) return;
        sb.append("- ").append(head).append(": ")
                .append(String.join(", ", types.stream().map(SpecialSupplyType::getLabel).toList())).append('\n');
    }

    private static List<SpecialSupplyType> concat(Set<SpecialSupplyType> a, Set<SpecialSupplyType> b) {
        List<SpecialSupplyType> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }
}
