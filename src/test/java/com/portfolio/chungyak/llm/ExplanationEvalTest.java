package com.portfolio.chungyak.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MatchResult} → 자연어 요약 <b>품질</b> eval.
 *
 * <p>ANTHROPIC_API_KEY 가 있을 때만 돈다. 시나리오 × 반복 만큼 실제 LLM 을 부른다
 * (기본 5 시나리오 × 2회 = 10콜). CI 자동 실행 금지 — 프롬프트/모델 변경 시 회귀 확인용.
 *
 * <p>여기서 부르는 건 {@link AnthropicExplainer} <b>원문</b>이다 —
 * {@link ExplanationService} 의 재시도·폴백을 거치지 않은, 모델 자체의 출력을 잰다.
 * 기대값은 하드코딩하지 않고 규칙 엔진 결과({@link MatchResult})에서 결정론적으로 끌어낸다:
 * <ul>
 *   <li><b>모순 없음</b> — {@link ContradictionCheck} 통과 (자격 유무를 뒤집지 않음). 핵심 지표.</li>
 *   <li>신청 가능 유형이 있으면 그 유형을 언급한다.</li>
 *   <li>신청 가능 유형이 없으면 "없다"고 말한다.</li>
 *   <li>"자격되나 물량 없음" 유형이 있으면 그 유형을 언급한다.</li>
 *   <li>미충족 근거에 수치가 있으면(개선 경로) 요약에도 수치가 나온다.</li>
 * </ul>
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ExplanationEvalTest {

    private static final double MIN_CONTRADICTION_FREE = 0.85;
    private static final double MIN_CLEAN_RATE         = 0.70;

    private record Scenario(String id, String note, MatchResult result) {}

    private static List<Scenario> scenarios() {
        return List.of(
                new Scenario("match-newlywed-firsttime", "신혼·생애최초 신청 가능", MatchResults.withMatch()),
                new Scenario("no-match", "혼인 아님·유주택 — 아무 유형도 자격 없음", MatchResults.noMatch()),
                new Scenario("shortfall-account-months", "다자녀 가능 + 신혼 통장 미달(규제지역, 12/24개월)", MatchResults.shortfall()),
                new Scenario("marriage-expired", "혼인 7년 초과 — 신혼 탈락, 생애최초는 가능", MatchResults.marriageExpired()),
                new Scenario("qualified-but-unavailable", "다자녀 자격 되나 공고에 물량 0", MatchResults.qualifiedButUnavailable()));
    }

    private AnthropicExplainer explainer;
    private String model;
    private int reps;

    @BeforeEach
    void setUp() {
        model = System.getenv().getOrDefault("LLM_MODEL", "claude-sonnet-5");
        reps = Integer.parseInt(System.getenv().getOrDefault("EVAL_REPS", "2"));
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY")).build();
        explainer = new AnthropicExplainer(client, new LlmProperties(System.getenv("ANTHROPIC_API_KEY"), model));
    }

    @Test
    @DisplayName("요약 품질 — 모순 없음 비율·완전 통과 비율이 임계값 이상")
    void summaryQuality() {
        int runs = 0, contradictionFree = 0, clean = 0;
        Map<String, List<String>> failuresById = new LinkedHashMap<>();

        for (Scenario s : scenarios()) {
            String facts = ExplanationFacts.format(s.result());
            for (int rep = 0; rep < reps; rep++) {
                runs++;
                String raw = explainer.explain(facts);
                boolean noContradiction = ContradictionCheck.check(s.result(), raw).isEmpty();
                if (noContradiction) contradictionFree++;

                List<String> fails = checkExpectations(s.result(), raw);
                if (!noContradiction) fails.add(0, "모순: " + ContradictionCheck.check(s.result(), raw).orElse("?"));
                if (fails.isEmpty()) {
                    clean++;
                } else {
                    failuresById.computeIfAbsent(s.id() + " (" + s.note() + ")", k -> new ArrayList<>())
                            .add("rep" + rep + ": " + String.join("; ", fails) + "\n        > " + oneLine(raw));
                }
            }
        }

        double contradictionFreeRate = (double) contradictionFree / runs;
        double cleanRate = (double) clean / runs;

        System.out.println(render(runs, contradictionFreeRate, cleanRate, failuresById));

        assertThat(contradictionFreeRate)
                .as("모순 없음 비율 — 요약이 자격 유무를 뒤집으면 안 됨").isGreaterThanOrEqualTo(MIN_CONTRADICTION_FREE);
        assertThat(cleanRate)
                .as("완전 통과 비율 (스코어카드 참고)").isGreaterThanOrEqualTo(MIN_CLEAN_RATE);
    }

    /** 규칙 엔진 결과에서 끌어낸 기대를 요약 텍스트가 지키는지 — 못 지킨 항목 목록. */
    private static List<String> checkExpectations(MatchResult r, String text) {
        List<String> fails = new ArrayList<>();

        if (r.hasAnyMatch()) {
            Set<String> labels = matchedLabels(r);
            if (labels.stream().noneMatch(l -> mentions(text, l))) {
                fails.add("신청 가능 유형 " + labels + " 을(를) 언급하지 않음");
            }
        } else if (!text.replaceAll("\\s+", "").contains("없")) {
            fails.add("신청 가능 유형이 없다는 내용이 없음");
        }

        for (SpecialSupplyType t : r.qualifiedButUnavailable()) {
            if (!mentions(text, t.getLabel())) {
                fails.add("자격되나 물량 없는 유형(" + t.getLabel() + ") 언급 없음");
            }
        }

        if (hasNumericShortfall(r) && !text.matches("(?s).*\\d.*")) {
            fails.add("미충족 근거에 수치가 있는데 요약에 수치가 없음 (개선 경로)");
        }
        return fails;
    }

    private static Set<String> matchedLabels(MatchResult r) {
        Set<String> labels = new TreeSet<>();
        r.matches().forEach(m -> m.applicableTypes().forEach(t -> labels.add(t.getLabel())));
        return labels;
    }

    private static boolean hasNumericShortfall(MatchResult r) {
        return r.decisions().values().stream()
                .filter(d -> !d.isEligible() && !d.isUndetermined())
                .flatMap(d -> d.getFailedReasons().stream())
                .anyMatch(reason -> reason.matches("(?s).*\\d.*"));
    }

    /** 모델이 "다자녀가구" 대신 "다자녀" 로 쓰는 등 라벨 축약을 허용. */
    private static boolean mentions(String text, String label) {
        return text.contains(label) || text.contains(label.replace("가구", ""));
    }

    private static String oneLine(String s) {
        String t = s.replaceAll("\\s+", " ").strip();
        return t.length() <= 160 ? t : t.substring(0, 160) + "…";
    }

    private static String render(int runs, double cfRate, double cleanRate,
                                 Map<String, List<String>> failuresById) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== 판정 요약 eval (runs=").append(runs).append(") ===\n");
        sb.append(String.format("  모순 없음 비율 : %.1f%%%n", cfRate * 100));
        sb.append(String.format("  완전 통과 비율 : %.1f%%%n", cleanRate * 100));
        if (failuresById.isEmpty()) {
            sb.append("  (모든 실행 통과)\n");
        } else {
            sb.append("\n  미달 실행:\n");
            failuresById.forEach((id, list) -> {
                sb.append("    ").append(id).append('\n');
                list.forEach(line -> sb.append("      - ").append(line).append('\n'));
            });
        }
        return sb.toString();
    }
}
