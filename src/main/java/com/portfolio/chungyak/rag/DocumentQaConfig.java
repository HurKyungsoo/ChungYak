package com.portfolio.chungyak.rag;

import com.anthropic.client.AnthropicClient;
import com.portfolio.chungyak.llm.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 공고문 Q&A 응답기 빈.
 *
 * {@link AnthropicClient} 빈이 있을 때만(= ANTHROPIC_API_KEY 설정 시) 만든다.
 * 없으면 {@link DocumentQaService} 가 Optional.empty() 를 받아 Q&A 를 끈다.
 */
@Slf4j
@Configuration
public class DocumentQaConfig {

    @Bean
    @ConditionalOnBean(AnthropicClient.class)
    public DocumentAnswerer anthropicDocumentAnswerer(AnthropicClient client, LlmProperties properties) {
        log.info("공고문 Q&A 응답기 활성화 — model={}", properties.model());
        return new AnthropicDocumentAnswerer(client, properties.model());
    }
}
