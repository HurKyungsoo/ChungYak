package com.portfolio.chungyak.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groq(OpenAI 호환) chat completions 한 번 호출 — {@code POST {baseUrl}/chat/completions}.
 *
 * 공식 Java SDK 를 쓰지 않고 {@link RestClient} 로 raw HTTP 를 친다(Voyage 와 같은 방식). 실패(429·5xx·타임아웃·
 * 빈 응답)는 예외로 올린다 — 호출하는 서비스가 잡아 기능만 끄고(폴백) 앱은 계속 돈다. 재시도는 하지 않는다:
 * 무료 등급의 429 는 잠시 뒤에도 같기 쉽고, 사용자를 기다리게 하는 것보다 폴백이 낫다.
 */
public class GroqChatClient {

    private final RestClient restClient;
    private final GroqProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    public GroqChatClient(RestClient groqRestClient, GroqProperties properties) {
        this.restClient = groqRestClient;
        this.properties = properties;
    }

    public String model() {
        return properties.model();
    }

    /**
     * @param json true 면 JSON 모드 — 응답이 유효한 JSON 객체 하나로 강제된다(프롬프트에 "JSON" 이 있어야 한다).
     * @return 응답 본문 텍스트(비면 예외)
     */
    public String chat(String system, String user, int maxTokens, boolean json) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("temperature", 0);   // 같은 입력이면 같은 출력에 가깝게 — 추출·요약에 창의성이 필요 없다
        body.put("max_tokens", maxTokens);
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        if (json) body.put("response_format", Map.of("type", "json_object"));

        String raw = restClient.post()
                .uri(properties.baseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode content = mapper.readTree(raw).path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new IllegalStateException("Groq 응답이 비어 있음");
            }
            return content.asText().strip();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Groq 응답을 읽지 못함", e);
        }
    }
}
