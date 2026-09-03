package com.portfolio.chungyak.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;

/**
 * {@link LlmProfileCaller} 의 Anthropic 구현.
 *
 * 이 클래스가 유일하게 Anthropic SDK 를 만지는 곳이다.
 * output_config(스키마) 로 응답 형식을 강제한다 — 프롬프트로 "JSON 으로만" 부탁하는 게
 * 아니라 API 가 스키마에 맞는 구조만 돌려준다. "애매하면 null" 은 필드가 nullable 인
 * 스키마 + 시스템 프롬프트 양쪽으로 보장한다.
 */
class AnthropicProfileCaller implements LlmProfileCaller {

    private static final long MAX_TOKENS = 2048L;

    private static final String SYSTEM_PROMPT = """
            너는 청약 상담 폼을 채워주는 보조 도구다.
            사용자가 자연어로 쓴 상황에서 청약 신청자 조건을 뽑아 구조화된 값으로만 답한다.

            반드시 지킬 것:
            - 문장에서 명시적으로 확인되는 값만 채운다.
            - 추측하지 않는다. 근거가 없거나 모호하면 그 필드는 null 로 둔다.
              예) "신혼인 것 같다" -> married=true 라도 혼인 기간은 알 수 없으므로
                  monthsSinceMarriage=null
              예) "부모님 모시고 산다" -> 나이·부양 기간이 불명확하면
                  supportingOldParents=null
            - 너는 자격을 판정하지 않는다. 되는지/안 되는지 판단하지 말고 값만 뽑는다.
            """;

    private final AnthropicClient client;
    private final String model;

    AnthropicProfileCaller(AnthropicClient client, LlmProperties properties) {
        this.client = client;
        this.model = properties.model();
    }

    @Override
    public ExtractedProfile call(String naturalText) {
        StructuredMessageCreateParams<ExtractedProfile> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .outputConfig(ExtractedProfile.class)
                .system(SYSTEM_PROMPT)
                .addUserMessage(naturalText)
                .build();

        return client.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(typed -> typed.text())
                .findFirst()
                .orElse(null);
    }
}
