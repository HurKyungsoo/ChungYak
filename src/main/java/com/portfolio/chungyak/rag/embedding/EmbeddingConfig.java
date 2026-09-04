package com.portfolio.chungyak.rag.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 임베딩 빈 구성.
 *
 * {@code rag.voyage.api-key} 가 없거나 REPLACE_ME 면 {@link EmbeddingClient} 빈이 없고,
 * 인덱서·검색 서비스는 {@code Optional.empty()} 를 받아 "비활성" 상태가 된다.
 * (LLM 기능이 ANTHROPIC_API_KEY 없이 꺼지는 것과 같은 패턴.)
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(VoyageProperties.class)
public class EmbeddingConfig {

    @Bean
    @ConditionalOnExpression(
            "'${rag.voyage.api-key:REPLACE_ME}' != 'REPLACE_ME' "
            + "&& '${rag.voyage.api-key:}'.length() > 0")
    public EmbeddingClient voyageEmbeddingClient(VoyageProperties properties) {
        log.info("Voyage 임베딩 클라이언트 활성화 — model={}", properties.model());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        RestClient restClient = RestClient.builder().requestFactory(factory).build();
        return new VoyageEmbeddingClient(restClient, properties);
    }
}
