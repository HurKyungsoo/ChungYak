package com.portfolio.chungyak.llm;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.RecordComponent;

/**
 * {@link LlmProfileCaller} 의 Groq 구현 — JSON 모드로 {@link ExtractedProfile} 을 받는다.
 *
 * Anthropic 쪽은 API 가 스키마를 강제하지만 여기서는 JSON 모드(유효한 JSON 객체)까지만 보장된다. 그래서
 * 프롬프트에 필드 목록·설명을 넣고(레코드의 {@code @JsonPropertyDescription} 에서 만들어 두 곳이 어긋나지 않는다),
 * 응답은 모르는 필드는 버리고 못 읽는 타입은 실패로 처리한다 — 실패하면 서비스가 FAILED 로 바꿔 사용자가 폼을 직접 채운다.
 * 추출값은 어차피 폼에서 사용자가 확인·수정한 뒤에만 판정에 들어간다(CLAUDE.md 절대 규칙 2).
 */
class GroqProfileCaller implements LlmProfileCaller {

    private static final int MAX_TOKENS = 1024;

    private final GroqChatClient client;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final String systemPrompt = buildSystemPrompt();

    GroqProfileCaller(GroqChatClient client) {
        this.client = client;
    }

    @Override
    public ExtractedProfile call(String naturalText) {
        String json = client.chat(systemPrompt, naturalText, MAX_TOKENS, true);
        try {
            return mapper.readValue(json, ExtractedProfile.class);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("추출 결과를 해석하지 못함", e);
        }
    }

    static String buildSystemPrompt() {
        StringBuilder fields = new StringBuilder();
        for (RecordComponent c : ExtractedProfile.class.getRecordComponents()) {
            JsonPropertyDescription d = c.getAccessor().getAnnotation(JsonPropertyDescription.class);
            if (d == null) d = c.getAnnotation(JsonPropertyDescription.class);
            fields.append("- ").append(c.getName()).append(" (")
                    .append(c.getType().getSimpleName()).append("): ")
                    .append(d == null ? "" : d.value()).append('\n');
        }
        return AnthropicProfileCaller.SYSTEM_PROMPT + """

                출력 형식: 아래 필드를 모두 키로 가진 JSON 객체 하나만 출력한다. 다른 글자는 쓰지 않는다.
                값을 알 수 없으면 그 키의 값은 null 이다 (키를 빼지 않는다).

                특히 주의: '작년', '재작년', '몇 년 전', '얼마 전', '최근'처럼 기준 시점이 없는 상대적 표현은
                개월 수로 환산하지 않는다 — 그 필드는 null 이다.
                예) "결혼은 재작년에 했어요" -> monthsSinceMarriage=null (24 로 추측하지 않는다)
                "결혼한 지 3년" 처럼 기간이 숫자로 직접 적힌 경우에만 개월 수로 바꾼다.

                """ + fields;
    }
}
