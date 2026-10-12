package com.portfolio.chungyak.llm;

import com.portfolio.chungyak.llm.ExtractionEvalScorer.CaseScore;
import com.portfolio.chungyak.llm.ProfileExtractionResult.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Groq 로 돌리는 자연어 추출 <b>품질</b> eval — {@link ProfileExtractionEvalTest}(Anthropic)와 같은 데이터셋·채점.
 *
 * <p>GROQ_API_KEY 가 있을 때만 돈다. 무료 등급이라 비용은 없지만 분당 토큰 한도(TPM)가 작아
 * 호출 사이에 간격을 두고, 429 면 한 번 기다렸다 다시 부른다. 프롬프트·모델을 바꾼 뒤 수동으로 돌려
 * "명시된 값만 뽑고 애매하면 null" 이 지켜지는지 본다 — 추출값은 사용자가 폼에서 확인하지만, 추측이 많으면
 * 확인 부담이 늘고 놓친 오값이 판정으로 갈 수 있다.
 */
@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
class ProfileExtractionGroqEvalTest {

    private static final long PACE_MS = 9_000;   // 8,000 TPM 에서 호출당 ~1k 토큰 → 분당 6회 안팎

    @Test
    @DisplayName("Groq 추출 품질 스코어카드")
    void extractionQuality() throws InterruptedException {
        String model = System.getenv().getOrDefault("GROQ_MODEL", new GroqProperties(null, null, null, false).model());
        GroqProperties props = new GroqProperties(System.getenv("GROQ_API_KEY"), model, null, false);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        GroqProfileCaller caller = new GroqProfileCaller(
                new GroqChatClient(RestClient.builder().requestFactory(factory).build(), props));

        // 서비스(ProfileExtractionService)를 거치지 않고 모델 출력을 직접 채점한다 — 서비스는 "값이 하나도 없으면
        // FAILED(조건을 읽지 못함)"로 바꾸는데, 근거 없는 문장에 전부 null 로 답한 건 모델이 맞게 한 것이다.
        // 호출 오류(429 등)일 때만 쉬었다 한 번 다시 부르고, 그래도 안 되면 FAILED 로 채점한다.
        List<CaseScore> scores = new ArrayList<>();
        for (ExtractionEvalCase c : ExtractionEvalCase.loadAll()) {
            ExtractedProfile p = null;
            String status = Status.EXTRACTED.name();
            for (int attempt = 0; attempt < 2 && p == null; attempt++) {
                try {
                    p = caller.call(c.text());
                } catch (RuntimeException e) {
                    System.out.println("호출 오류 " + c.id() + ": " + e);
                    if (attempt == 0) Thread.sleep(30_000);
                }
            }
            if (p == null) status = Status.FAILED.name();
            scores.add(ExtractionEvalScorer.score(c, status, p));
            Thread.sleep(PACE_MS);
        }

        ExtractionEvalScorecard card = ExtractionEvalScorecard.of(scores, "groq:" + model);
        System.out.println(card.render());

        // 임계값은 Anthropic eval 과 같은 기준 — 못 넘으면 Groq 모델을 기본으로 쓰기엔 이르다는 신호
        assertThat(card.nullDiscipline()).as("null 원칙 준수율").isGreaterThanOrEqualTo(0.90);
        assertThat(card.fieldAccuracy()).as("필드 정확도").isGreaterThanOrEqualTo(0.85);
    }
}
