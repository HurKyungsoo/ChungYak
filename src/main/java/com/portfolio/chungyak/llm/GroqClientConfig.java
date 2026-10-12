package com.portfolio.chungyak.llm;

import com.portfolio.chungyak.rag.DocumentAnswerer;
import com.portfolio.chungyak.rag.GroqDocumentAnswerer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Groq(무료 등급) LLM 빈 구성 — Anthropic 키가 없고 GROQ_API_KEY 가 있을 때만.
 *
 * 둘 다 있으면 Anthropic 이 우선이다(이 설정 전체가 꺼진다). 어느 쪽도 없으면 LLM 기능이 모두 꺼지고
 * 앱은 정상 동작한다 — 판정·배지·"이것만 바뀌면 가능"은 원래 규칙 엔진만 쓴다.
 *
 * 역할은 Anthropic 과 같다: 자연어 -> 조건 추출, (선택) 판정 요약 재구성, 공고문 Q&A 답변. 판정에는 관여하지 않는다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(GroqProperties.class)
@ConditionalOnExpression(
        "'${llm.groq.api-key:REPLACE_ME}' != 'REPLACE_ME' && '${llm.groq.api-key:}'.length() > 0"
        + " && ('${llm.anthropic.api-key:REPLACE_ME}' == 'REPLACE_ME' || '${llm.anthropic.api-key:}'.length() == 0)")
public class GroqClientConfig {

    @Bean
    public GroqChatClient groqChatClient(GroqProperties properties) {
        log.info("Groq 클라이언트 활성화 — model={}, 판정 요약 사용={}", properties.model(), properties.explainEnabled());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return new GroqChatClient(RestClient.builder().requestFactory(factory).build(), properties);
    }

    @Bean
    public LlmProfileCaller groqProfileCaller(GroqChatClient client) {
        return new GroqProfileCaller(client);
    }

    /** 요약 입력에는 소득·자산 등 판정 근거가 들어간다 — 명시적으로 켠 경우에만 외부로 보낸다. */
    @Bean
    @ConditionalOnProperty(name = "llm.groq.explain-enabled", havingValue = "true")
    public LlmExplainer groqExplainer(GroqChatClient client) {
        return new GroqExplainer(client);
    }

    @Bean
    public DocumentAnswerer groqDocumentAnswerer(GroqChatClient client) {
        return new GroqDocumentAnswerer(client);
    }
}
