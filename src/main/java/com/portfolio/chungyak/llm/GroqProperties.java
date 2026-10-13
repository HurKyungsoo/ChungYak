package com.portfolio.chungyak.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Groq 설정 (llm.groq.*) — 무료 등급 대안. ANTHROPIC_API_KEY 가 없을 때만 쓴다(있으면 Anthropic 이 우선).
 *
 * @param explainEnabled 판정 요약(LlmExplainer)에도 쓸지. 기본 false — 요약 입력에는 소득·자산 같은 판정 근거가
 *                       들어가므로 무료 등급(데이터 처리 조건이 유료 API 와 다를 수 있음)으로 내보내지 않는다.
 *                       꺼두면 요약은 결정론적 문장(FallbackSummary)으로 대체된다.
 */
@ConfigurationProperties(prefix = "llm.groq")
public record GroqProperties(String apiKey, String model, String baseUrl, boolean explainEnabled,
                             String qaModel, String qaReasoningEffort) {

    @ConstructorBinding   // 편의 생성자가 있어 바인딩할 생성자를 지정해야 한다
    public GroqProperties {
        // 무료 등급 모델 목록은 바뀐다 — llama-3.3-70b-versatile 은 404(model_not_found) 였다. 안 되면 GET /openai/v1/models 로 확인해 GROQ_MODEL 을 바꾼다.
        if (model == null || model.isBlank()) model = "openai/gpt-oss-120b";
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.groq.com/openai/v1";
        // 공고문 Q&A 전용 모델(#32) — 무료 등급 하루 토큰 한도(200K)가 모델별로 걸려(429 메시지가 모델을 지목),
        // Q&A 를 다른 모델로 돌리면 자연어 추출과 한도를 나눠 쓰지 않는다. 비우면 model 과 같다.
        if (qaModel == null || qaModel.isBlank()) qaModel = model;
        // Q&A 의 gpt-oss reasoning_effort(low/medium/high) — 추론 토큰 양. 비우면 보내지 않는다(Groq 기본값).
        // Q&A 만이다: 자연어 추출은 eval(98%)로 검증한 설정을 그대로 둔다.
        if (qaReasoningEffort != null && qaReasoningEffort.isBlank()) qaReasoningEffort = null;
    }

    /** 기존 호출부(테스트·eval)용 — Q&A 모델은 model 과 같고 Q&A reasoning_effort 는 보내지 않는다 */
    public GroqProperties(String apiKey, String model, String baseUrl, boolean explainEnabled) {
        this(apiKey, model, baseUrl, explainEnabled, null, null);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"REPLACE_ME".equals(apiKey);
    }
}
