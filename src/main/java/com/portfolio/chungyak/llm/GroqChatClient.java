package com.portfolio.chungyak.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
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
@Slf4j
public class GroqChatClient {

    private final RestClient restClient;
    private final GroqProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    /** 토큰·429 카운터(#25) — 없으면(테스트·eval) 세지 않는다 */
    private final LlmUsageStats stats;

    public GroqChatClient(RestClient groqRestClient, GroqProperties properties) {
        this(groqRestClient, properties, null);
    }

    public GroqChatClient(RestClient groqRestClient, GroqProperties properties, LlmUsageStats stats) {
        this.restClient = groqRestClient;
        this.properties = properties;
        this.stats = stats;
    }

    public String model() {
        return properties.model();
    }

    /**
     * @param json true 면 JSON 모드 — 응답이 유효한 JSON 객체 하나로 강제된다(프롬프트에 "JSON" 이 있어야 한다).
     * @return 응답 본문 텍스트(비면 예외)
     */
    public String chat(String system, String user, int maxTokens, boolean json) {
        return chat(properties.model(), null, system, user, maxTokens, json);
    }

    /**
     * 공고문 Q&A 호출 (#32) — {@link GroqProperties#qaModel()} 과 {@link GroqProperties#qaReasoningEffort()} 로.
     * 자연어 추출은 eval 로 검증한 설정 그대로 두고 Q&A 만 바꾸려고 따로 둔다.
     */
    public String qa(String system, String user, int maxTokens) {
        return chat(properties.qaModel(), properties.qaReasoningEffort(), system, user, maxTokens, false);
    }

    /**
     * @param model           이 호출에 쓸 모델 — 용도별로 무료 한도(모델별)를 나눠 쓰려고(#32)
     * @param reasoningEffort gpt-oss 의 추론 토큰 양. null 이면 보내지 않는다(Groq 기본값).
     *                        추론 토큰도 max_tokens 안에서 쓰여, 기본값에서 1,024 를 추론에 다 써 답이 빈 적이 있다.
     */
    String chat(String model, String reasoningEffort, String system, String user, int maxTokens, boolean json) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        if (reasoningEffort != null && model.startsWith("openai/gpt-oss")) {
            body.put("reasoning_effort", reasoningEffort);
        }
        body.put("temperature", 0);   // 같은 입력이면 같은 출력에 가깝게 — 추출·요약에 창의성이 필요 없다
        body.put("max_tokens", maxTokens);
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        if (json) body.put("response_format", Map.of("type", "json_object"));

        String raw;
        try {
            raw = restClient.post()
                    .uri(properties.baseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            // 무료 등급 분당·하루 토큰 한도(#32) — 다른 실패와 구분해 화면이 "잠시 뒤 다시"라고 알리게 한다(#25)
            if (stats != null) stats.count("groq.429." + model);
            throw new LlmRateLimitedException("Groq 요청 한도 초과 — model=" + model, e);
        }

        try {
            JsonNode root = mapper.readTree(raw);
            logUsage(model, root.path("usage"));
            if (stats != null && !root.path("usage").isMissingNode()) {
                stats.tokens(model, root.path("usage").path("total_tokens").asLong());
            }
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new IllegalStateException("Groq 응답이 비어 있음");
            }
            return content.asText().strip();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Groq 응답을 읽지 못함", e);
        }
    }

    /**
     * 토큰 사용량 로그 (#32) — 무료 등급은 하루 토큰 한도(200K)가 상한이라, 기능별로 얼마나 쓰는지 알아야
     * 발췌 수·모델·reasoning_effort 를 고를 수 있다. reasoning 토큰은 응답에 있을 때만 적는다.
     */
    private static void logUsage(String model, JsonNode usage) {
        if (usage.isMissingNode()) return;
        JsonNode reasoning = usage.path("completion_tokens_details").path("reasoning_tokens");
        log.info("Groq 토큰 — model={} prompt={} completion={}{} total={}", model,
                usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt(),
                reasoning.isMissingNode() ? "" : " (reasoning " + reasoning.asInt() + ")",
                usage.path("total_tokens").asInt());
    }
}
