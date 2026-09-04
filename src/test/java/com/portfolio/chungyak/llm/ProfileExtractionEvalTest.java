package com.portfolio.chungyak.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.portfolio.chungyak.llm.ExtractionEvalScorer.CaseScore;
import com.portfolio.chungyak.llm.ProfileExtractionResult.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 자연어 → {@link ExtractedProfile} 추출 <b>품질</b> eval.
 *
 * <p>ANTHROPIC_API_KEY 가 있을 때만 돈다. 매 실행마다 실제 비용이 발생한다
 * (데이터셋 {@value #DATASET} 건 × 1콜, claude-sonnet-5 기준 대략 수십 원).
 * CI 에서 자동으로 돌리지 말고, 프롬프트/스키마/모델을 바꾼 뒤 회귀 확인용으로 수동 실행한다.
 *
 * <p>측정 대상은 프롬프트+스키마가 "명시된 값만 뽑고 애매하면 null" 을 지키는지다
 * (CLAUDE.md 절대 규칙 — 추출값이 판정으로 직행하므로 추측이 곧 오판이다).
 * 임계값은 {@code MIN_*} 상수 — 첫 실행 뒤 스코어카드를 보고 조정한다.
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ProfileExtractionEvalTest {

    static final String DATASET = "/eval/profile-extraction-cases.json";

    private static final double MIN_FIELD_ACCURACY  = 0.85;
    private static final double MIN_NULL_DISCIPLINE = 0.90;
    private static final double MIN_CASE_PASS_RATE  = 0.65;

    private ProfileExtractionService service;
    private String model;

    @BeforeEach
    void setUp() {
        model = System.getenv().getOrDefault("LLM_MODEL", "claude-sonnet-5");
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY")).build();
        LlmProperties props = new LlmProperties(System.getenv("ANTHROPIC_API_KEY"), model);
        service = new ProfileExtractionService(Optional.of(new AnthropicProfileCaller(client, props)));
    }

    @Test
    @DisplayName("추출 품질 — 필드 정확도·null 원칙 준수·케이스 통과율이 임계값 이상")
    void extractionQuality() {
        List<ExtractionEvalCase> cases = ExtractionEvalCase.loadAll();
        List<CaseScore> scores = new ArrayList<>();

        for (ExtractionEvalCase c : cases) {
            ProfileExtractionResult r = service.extract(c.text());
            ExtractedProfile p = r.status() == Status.EXTRACTED ? r.profile() : null;
            scores.add(ExtractionEvalScorer.score(c, r.status().name(), p));
        }

        ExtractionEvalScorecard card = ExtractionEvalScorecard.of(scores, model);
        System.out.println(card.render());

        assertThat(card.fieldAccuracy())
                .as("필드 정확도 (스코어카드 참고)").isGreaterThanOrEqualTo(MIN_FIELD_ACCURACY);
        assertThat(card.nullDiscipline())
                .as("null 원칙 준수율 — 언급 없는 필드를 추측하면 안 됨").isGreaterThanOrEqualTo(MIN_NULL_DISCIPLINE);
        assertThat(card.casePassRate())
                .as("케이스 전체 통과율").isGreaterThanOrEqualTo(MIN_CASE_PASS_RATE);
    }
}
