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
@EnableConfigurationProperties({VoyageProperties.class, RerankProperties.class})
public class EmbeddingConfig {

    @Bean
    @ConditionalOnExpression(
            "'${rag.voyage.api-key:REPLACE_ME}' != 'REPLACE_ME' "
            + "&& '${rag.voyage.api-key:}'.length() > 0")
    public EmbeddingClient voyageEmbeddingClient(VoyageProperties properties) {
        log.warn("유료 API 키 로드됨 — Voyage 임베딩 클라이언트 활성화(호출마다 과금), model={}", properties.model());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        RestClient restClient = RestClient.builder().requestFactory(factory).build();
        return new VoyageEmbeddingClient(restClient, properties);
    }

    /** 공고문 Q&A 재정렬 — 임베딩과 같은 키, {@code rag.rerank.enabled=false} 로 끈다 */
    @Bean
    @ConditionalOnExpression(
            "'${rag.voyage.api-key:REPLACE_ME}' != 'REPLACE_ME' "
            + "&& '${rag.voyage.api-key:}'.length() > 0 && ${rag.rerank.enabled:true}")
    public Reranker voyageReranker(RerankProperties rerank, VoyageProperties voyage) {
        log.warn("유료 API 키 로드됨 — Voyage rerank 활성화(질문마다 과금), model={}, 후보 {}개", rerank.model(), rerank.candidates());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new VoyageReranker(RestClient.builder().requestFactory(factory).build(), rerank, voyage.apiKey());
    }
}
