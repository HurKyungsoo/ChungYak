package com.portfolio.chungyak.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.time.Duration;
import com.portfolio.chungyak.rule.EligibilityDecision;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MatchResult} → 자연어 요약 <b>품질</b> eval.
 *
 * <p>ANTHROPIC_API_KEY(또는 GROQ_API_KEY) 가 있을 때만 돈다. 시나리오 × 반복 만큼 실제 LLM 을 부른다
 * (기본 6 시나리오 × 2회 = 12콜). CI 자동 실행 금지 — 프롬프트/모델 변경 시 회귀 확인용.
 *
 * <p>여기서 부르는 건 {@link AnthropicExplainer} <b>원문</b>이다 —
 * {@link ExplanationService} 의 재시도·폴백을 거치지 않은, 모델 자체의 출력을 잰다.
 * 역할은 "다음에 할 일" 한두 문장이다(#20). 기대값은 하드코딩하지 않고 규칙 엔진 결과에서 끌어낸다:
 * <ul>
 *   <li><b>모순 없음</b> — {@link ContradictionCheck} 통과 (판정 뒤집기·지어낸 숫자·엉뚱한 유형의 보완 수치). 핵심 지표.</li>
 *   <li>형식 — 마크다운 없음, 3문장 이하 ({@link ContradictionCheck#formatProblem}).</li>
 *   <li>판정 문장("신청 가능합니다" 등)을 쓰지 않는다.</li>
 *   <li>보완하면 가능한 유형이 있으면 수치(또는 예치금)로 안내, 없고 판정 보류면 필요한 입력, 둘 다 없으면 공고문·일정.</li>
 * </ul>
 */
@EnabledIf("hasAnyKey")
class ExplanationEvalTest {

    /**
     * Anthropic 키가 있으면 그것을(유료), 없고 GROQ_API_KEY 만 있으면 Groq 를 잰다.
     * EVAL_PROVIDER=groq 이면 Anthropic 키가 환경에 있어도 Groq 만 쓴다 — 유료 호출을 실수로 하지 않게.
     */
    static boolean hasAnyKey() {
        return useAnthropic() || notBlank(System.getenv("GROQ_API_KEY"));
    }

    private static boolean useAnthropic() {
        return notBlank(System.getenv("ANTHROPIC_API_KEY")) && !"groq".equals(System.getenv("EVAL_PROVIDER"));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static final double MIN_CONTRADICTION_FREE = 0.85;
    private static final double MIN_CLEAN_RATE         = 0.70;

    private record Scenario(String id, String note, MatchResult result) {}

    private static List<Scenario> scenarios() {
        return List.of(
                new Scenario("match-newlywed-firsttime", "신혼·생애최초 신청 가능", MatchResults.withMatch()),
                new Scenario("no-match", "혼인 아님·유주택 — 아무 유형도 자격 없음", MatchResults.noMatch()),
                new Scenario("shortfall-account-months", "다자녀 가능 + 신혼 통장 미달(규제지역, 12/24개월)", MatchResults.shortfall()),
                new Scenario("marriage-expired", "혼인 7년 초과 — 신혼 탈락, 생애최초는 가능", MatchResults.marriageExpired()),
                new Scenario("qualified-but-unavailable", "다자녀 자격 되나 공고에 물량 0", MatchResults.qualifiedButUnavailable()),
                new Scenario("deposit-shortfall", "민영 예치금 부족 — 힌트 금액은 [금액]으로 가려짐", MatchResults.depositShortfall()));
    }

    private LlmExplainer explainer;
    private boolean groq;
    private String model;
    private int reps;

    @BeforeEach
    void setUp() {
        reps = Integer.parseInt(System.getenv().getOrDefault("EVAL_REPS", "2"));
        if (useAnthropic()) {
            model = System.getenv().getOrDefault("LLM_MODEL", "claude-sonnet-5");
            AnthropicClient client = AnthropicOkHttpClient.builder()
                    .apiKey(System.getenv("ANTHROPIC_API_KEY")).build();
            explainer = new AnthropicExplainer(client, new LlmProperties(System.getenv("ANTHROPIC_API_KEY"), model));
        } else {
            groq = true;   // 무료 등급 분당 토큰 한도 때문에 호출 사이에 간격을 둔다
            GroqProperties props = new GroqProperties(System.getenv("GROQ_API_KEY"), System.getenv("GROQ_MODEL"), null, true);
            model = "groq:" + props.model();
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(5));
            factory.setReadTimeout(Duration.ofSeconds(30));
            explainer = new GroqExplainer(new GroqChatClient(RestClient.builder().requestFactory(factory).build(), props));
        }
    }

    @Test
    @DisplayName("요약 품질 — 모순 없음 비율·완전 통과 비율이 임계값 이상")
    void summaryQuality() {
        int runs = 0, contradictionFree = 0, clean = 0;
        Map<String, List<String>> failuresById = new LinkedHashMap<>();
        Map<String, List<String>> outputsById = new LinkedHashMap<>();   // 사람이 읽고 비교할 출력 전부 (#20 완료 기준)

        for (Scenario s : scenarios()) {
            String facts = ExplanationFacts.format(s.result());
            for (int rep = 0; rep < reps; rep++) {
                runs++;
                String raw = explain(facts);
                outputsById.computeIfAbsent(s.id() + " (" + s.note() + ")", k -> new ArrayList<>()).add(oneLine(raw));
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

        String report = render(runs, contradictionFreeRate, cleanRate, failuresById) + renderOutputs(outputsById);
        System.out.println(report);
        writeReport(report);

        assertThat(contradictionFreeRate)
                .as("모순 없음 비율 — 요약이 자격 유무를 뒤집으면 안 됨").isGreaterThanOrEqualTo(MIN_CONTRADICTION_FREE);
        assertThat(cleanRate)
                .as("완전 통과 비율 (스코어카드 참고)").isGreaterThanOrEqualTo(MIN_CLEAN_RATE);
    }

    private String explain(String facts) {
        if (!groq) return explainer.explain(facts);
        try {
            Thread.sleep(10_000);
            return explainer.explain(facts);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (RuntimeException e) {   // 429 등 — 한 번 쉬었다 다시
            try { Thread.sleep(30_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            return explainer.explain(facts);
        }
    }

    /** 판정 문장 — "다음에 할 일" 역할(#20)에서는 쓰면 안 된다(결론은 요약 카드가 말한다) */
    private static final List<String> VERDICT_WORDS = List.of(
            "신청가능합니다", "신청할수있습니다", "신청할수없습니다", "자격이있습니다", "자격이없습니다", "자격을충족합니다");

    /**
     * 규칙 엔진 결과에서 끌어낸 기대를 "다음에 할 일" 문장이 지키는지 — 못 지킨 항목 목록.
     * 우선순위는 프롬프트와 같다: 보완하면 가능한 유형의 힌트 → 판정에 필요한 입력 → 공고문·일정 확인.
     */
    private static List<String> checkExpectations(MatchResult r, String text) {
        List<String> fails = new ArrayList<>();
        ContradictionCheck.formatProblem(text).ifPresent(p -> fails.add("형식: " + p));

        String compact = text.replaceAll("\\s+", "");
        VERDICT_WORDS.stream().filter(compact::contains).findFirst()
                .ifPresent(w -> fails.add("판정 문장을 씀(" + w + ")"));

        List<EligibilityDecision> fixable = r.decisions().values().stream().filter(EligibilityDecision::isFixable).toList();
        List<EligibilityDecision> undetermined = r.decisions().values().stream()
                .filter(d -> !d.isEligible() && d.isUndetermined()).toList();
        if (!fixable.isEmpty()) {
            if (!text.matches("(?s).*\\d.*") && !text.contains("예치금")) {
                fails.add("보완 힌트가 있는데 수치(또는 예치금) 안내가 없음");
            }
        } else if (!undetermined.isEmpty()) {
            boolean namesInput = undetermined.stream().flatMap(d -> d.getMissingInputs().stream())
                    .anyMatch(in -> compact.contains(in.replaceAll("\\s+", "").split("[·(]")[0]));
            if (!namesInput) fails.add("판정 보류인데 필요한 입력을 말하지 않음");
        } else if (r.hasAnyMatch() && !text.contains("공고문") && !text.contains("일정")) {
            fails.add("할 일이 없을 때 공고문·일정 확인 안내가 없음");
        } else if (!r.hasAnyMatch() && text.contains("접수")) {
            fails.add("신청 가능한 유형이 없는데 접수 준비를 권함");
        }
        return fails;
    }

    private static String oneLine(String s) {
        String t = s.replaceAll("\\s+", " ").strip();
        return t.length() <= 1200 ? t : t.substring(0, 1200) + "…";
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

    private static String renderOutputs(Map<String, List<String>> outputsById) {
        StringBuilder sb = new StringBuilder("\n  전체 출력:\n");
        outputsById.forEach((id, list) -> {
            sb.append("    ").append(id).append('\n');
            list.forEach(o -> sb.append("      > ").append(o).append('\n'));
        });
        return sb.toString();
    }

    /** 콘솔 인코딩(Windows)과 무관하게 읽을 수 있도록 UTF-8 파일로도 남긴다 — build/eval/explanation-eval.txt */
    private static void writeReport(String report) {
        try {
            java.nio.file.Path out = java.nio.file.Path.of("build", "eval", "explanation-eval.txt");
            java.nio.file.Files.createDirectories(out.getParent());
            java.nio.file.Files.writeString(out, report, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            System.out.println("eval 리포트 파일 저장 실패: " + e);
        }
    }
}
