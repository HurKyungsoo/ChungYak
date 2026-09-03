package com.portfolio.chungyak.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 설정.
 *
 * api-key 가 비어 있거나 REPLACE_ME 면 추출 기능이 비활성화된다
 * (앱은 정상 기동하고 다른 화면은 그대로 동작한다).
 */
@ConfigurationProperties(prefix = "llm.anthropic")
public record LlmProperties(String apiKey, String model) {

    public LlmProperties {
        if (model == null || model.isBlank()) {
            model = "claude-sonnet-5";
        }
    }

    /** 실제 키가 주입됐는지 — 자리표시자(REPLACE_ME)나 빈 값은 미설정으로 본다. */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"REPLACE_ME".equals(apiKey);
    }
}
