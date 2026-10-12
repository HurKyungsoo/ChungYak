package com.portfolio.chungyak.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Groq 설정 (llm.groq.*) — 무료 등급 대안. ANTHROPIC_API_KEY 가 없을 때만 쓴다(있으면 Anthropic 이 우선).
 *
 * @param explainEnabled 판정 요약(LlmExplainer)에도 쓸지. 기본 false — 요약 입력에는 소득·자산 같은 판정 근거가
 *                       들어가므로 무료 등급(데이터 처리 조건이 유료 API 와 다를 수 있음)으로 내보내지 않는다.
 *                       꺼두면 요약은 결정론적 문장(FallbackSummary)으로 대체된다.
 */
@ConfigurationProperties(prefix = "llm.groq")
public record GroqProperties(String apiKey, String model, String baseUrl, boolean explainEnabled) {

    public GroqProperties {
        // 무료 등급에서 제공되는 모델은 바뀔 수 있다 — 안 되면 GROQ_MODEL 로 바꾼다.
        if (model == null || model.isBlank()) model = "llama-3.3-70b-versatile";
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.groq.com/openai/v1";
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"REPLACE_ME".equals(apiKey);
    }
}
