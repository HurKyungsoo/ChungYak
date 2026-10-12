package com.portfolio.chungyak.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Groq(OpenAI 호환) 호출 형태와 응답 처리 — 실제 API 없이 검증. */
class GroqClientTest {

    private static final String URL = "https://api.groq.com/openai/v1/chat/completions";
    private final GroqProperties props = new GroqProperties("gsk_test", "test-model", null, false);

    private MockRestServiceServer server;

    private GroqChatClient clientWithServer() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new GroqChatClient(builder.build(), props);
    }

    private static String completion(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + quote(content) + "}}]}";
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    @Test
    @DisplayName("chat completions 로 보낸다: Bearer 키·모델·system/user·temperature 0·JSON 모드")
    void requestShape() {
        GroqChatClient client = clientWithServer();
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer gsk_test"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.temperature").value(0))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("시스템"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("사용자"))
                .andRespond(withSuccess(completion("{\"a\":1}"), MediaType.APPLICATION_JSON));

        assertThat(client.chat("시스템", "사용자", 100, true)).isEqualTo("{\"a\":1}");
        server.verify();
    }

    @Test
    @DisplayName("JSON 모드가 아니면 response_format 을 보내지 않는다")
    void plainModeOmitsResponseFormat() {
        GroqChatClient client = clientWithServer();
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.response_format").doesNotExist())
                .andRespond(withSuccess(completion("요약"), MediaType.APPLICATION_JSON));

        assertThat(client.chat("s", "u", 100, false)).isEqualTo("요약");
    }

    @Test
    @DisplayName("429·5xx 와 빈 응답은 예외 — 서비스가 잡아 폴백한다")
    void failuresThrow() {
        GroqChatClient client = clientWithServer();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> client.chat("s", "u", 10, false)).isInstanceOf(RuntimeException.class);

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.chat("s", "u", 10, false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("비어");
    }

    @Test
    @DisplayName("추출: JSON 을 ExtractedProfile 로 — 모르는 키는 버리고 null 은 null 로 둔다(추측하지 않음)")
    void profileCallerParses() {
        GroqChatClient client = clientWithServer();
        server.expect(requestTo(URL)).andRespond(withSuccess(completion(
                "{\"married\":true,\"monthsSinceMarriage\":null,\"childCount\":2,\"extra\":\"무시\"}"),
                MediaType.APPLICATION_JSON));

        ExtractedProfile p = new GroqProfileCaller(client).call("결혼했고 아이가 둘이에요");

        assertThat(p.married()).isTrue();
        assertThat(p.childCount()).isEqualTo(2);
        assertThat(p.monthsSinceMarriage()).isNull();
        assertThat(p.houseless()).isNull();   // 응답에 키가 없어도 null
    }

    @Test
    @DisplayName("추출: JSON 이 아니거나 타입이 틀리면 실패로 올린다 — 서비스가 FAILED 로 바꿔 폼을 직접 채우게 한다")
    void profileCallerRejectsGarbage() {
        GroqChatClient client = clientWithServer();
        server.expect(requestTo(URL)).andRespond(withSuccess(completion("죄송합니다, 읽을 수 없어요"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> new GroqProfileCaller(client).call("x")).isInstanceOf(RuntimeException.class);

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess(completion("{\"childCount\":\"많이\"}"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> new GroqProfileCaller(client).call("x")).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("프롬프트: 추측 금지 규칙 + 모든 ExtractedProfile 필드와 설명이 들어간다 (레코드가 바뀌어도 어긋나지 않는다)")
    void promptListsEveryField() {
        String prompt = GroqProfileCaller.buildSystemPrompt();

        assertThat(prompt).contains("추측하지 않는다").contains("JSON");
        assertThat(prompt).contains("재작년");   // 상대적 시점은 개월로 환산하지 않는다(eval 에서 24 로 추측한 사례)
        for (var c : ExtractedProfile.class.getRecordComponents()) {
            assertThat(prompt).as("필드 %s", c.getName()).contains("- " + c.getName() + " (");
        }
        assertThat(prompt).contains("혼인 중이면 true");   // @JsonPropertyDescription 이 따라온다
    }

    @Test
    @DisplayName("요약: Anthropic 과 같은 시스템 프롬프트(새 판정·숫자 금지)로 부른다")
    void explainerUsesSharedPrompt() {
        GroqChatClient client = clientWithServer();
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages[0].content").value(AnthropicExplainer.SYSTEM_PROMPT))
                .andExpect(jsonPath("$.messages[1].content").value("판정 근거"))
                .andRespond(withSuccess(completion("정리한 문장"), MediaType.APPLICATION_JSON));

        assertThat(new GroqExplainer(client).explain("판정 근거")).isEqualTo("정리한 문장");
    }
}
