package com.portfolio.chungyak.rag.embedding;

import java.util.List;

/**
 * 질문과 후보 발췌의 관련도로 순서를 다시 매긴다 — 공고문 Q&A 검색의 마지막 단계.
 *
 * 판정과 무관하다(정보 검색). 하이브리드 검색 상위 {@link #candidates()} 개를 받아 다시 매기고, 그중 앞쪽만 LLM 에 넘긴다.
 * 69문항 eval(2026-10-14, {@code NoticeQaSemanticEvalTest#rerankAndHeads}): 하이브리드 49 → rerank 상위 20 에서 61.
 */
public interface Reranker {

    /** @return 관련도 높은 순의 원래 인덱스 (documents 와 같은 개수) */
    List<Integer> order(String query, List<String> documents);

    /** 다시 매길 후보 수 — 많을수록 정확하지만 토큰(≈ 후보당 700)과 지연이 는다 */
    int candidates();

    String model();
}
