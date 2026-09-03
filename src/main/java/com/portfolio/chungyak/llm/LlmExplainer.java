package com.portfolio.chungyak.llm;

/**
 * 이미 확정된 판정 근거(텍스트) -> 자연어 요약 한 번 호출.
 *
 * SDK 의존은 {@code AnthropicExplainer} 에만. 서비스는 이 인터페이스만 안다.
 */
public interface LlmExplainer {

    /**
     * @param facts 규칙 엔진이 낸 판정과 근거를 정리한 텍스트
     * @return 자연어 요약. 비거나 null 이면 서비스가 재시도/폴백한다.
     * @throws RuntimeException 호출 실패 (서비스가 잡아 폴백)
     */
    String explain(String facts);
}
