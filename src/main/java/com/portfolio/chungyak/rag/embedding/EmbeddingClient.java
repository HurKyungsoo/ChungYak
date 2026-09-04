package com.portfolio.chungyak.rag.embedding;

import java.util.List;

/**
 * 텍스트 → 임베딩 벡터.
 *
 * Anthropic 은 임베딩 API 가 없어 별도 provider(Voyage AI)를 쓴다.
 * 이 인터페이스로 감싸 나머지 코드가 provider 를 모르게 한다.
 * 키가 없으면 빈 {@code Optional} 로 주입돼(구현 빈 미생성) RAG 인덱싱이 비활성된다 —
 * 앱의 나머지는 그대로 동작한다(LLM 설명 기능과 같은 패턴).
 */
public interface EmbeddingClient {

    /** 임베딩 대상 종류 — 검색 품질을 위해 문서/질의를 구분한다(Voyage input_type). */
    enum InputType { DOCUMENT, QUERY }

    /**
     * @return 입력 순서와 1:1 로 대응하는 벡터 목록. 모든 벡터는 같은 차원.
     */
    List<float[]> embed(List<String> texts, InputType type);

    /** 이 클라이언트가 내는 벡터의 모델 식별자 — 청크에 함께 저장해 재인덱싱 판단에 쓴다. */
    String model();
}
