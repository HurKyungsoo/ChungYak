package com.portfolio.chungyak.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 빈 구성.
 *
 * api-key 가 없거나 REPLACE_ME 면 {@link AnthropicClient} 빈을 만들지 않고,
 * 따라서 {@link LlmProfileCaller} 도 없다. {@link ProfileExtractionService} 는
 * Optional.empty() 를 받아 "비활성화" 상태가 되고, 앱의 나머지는 정상 동작한다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class AnthropicClientConfig {

    @Bean
    @ConditionalOnExpression(
            "'${llm.anthropic.api-key:REPLACE_ME}' != 'REPLACE_ME' "
            + "&& '${llm.anthropic.api-key:}'.length() > 0")
    public AnthropicClient anthropicClient(LlmProperties properties) {
        log.info("Anthropic 클라이언트 활성화 — model={}", properties.model());
        return AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .build();
    }

    @Bean
    @ConditionalOnBean(AnthropicClient.class)
    public LlmProfileCaller anthropicProfileCaller(AnthropicClient client, LlmProperties properties) {
        return new AnthropicProfileCaller(client, properties);
    }
}
