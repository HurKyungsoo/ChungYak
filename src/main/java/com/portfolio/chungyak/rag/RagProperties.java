package com.portfolio.chungyak.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 인덱싱·검색 파라미터 (rag.*). 코드에 박지 않고 배포 없이 조정한다.
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(Chunk chunk, Search search, Qa qa) {

    public RagProperties {
        if (chunk == null) chunk = new Chunk(0, 0);
        if (search == null) search = new Search(0, 0);
        if (qa == null) qa = new Qa(0, 0, 0);
    }

    /** 청크 목표 길이·겹침 (문자 수). */
    public record Chunk(int size, int overlap) {
        public Chunk {
            if (size <= 0) size = 900;
            if (overlap < 0 || overlap >= size) overlap = Math.min(150, size / 4);
        }
    }

    /** hybridWeight: 하이브리드 순위 융합(RRF)에서 벡터 쪽 가중치 (0..1, 1이면 사실상 벡터 전용). */
    public record Search(int topK, double hybridWeight) {
        public Search {
            if (topK <= 0) topK = 5;
            if (hybridWeight <= 0 || hybridWeight > 1) hybridWeight = 0.6;
        }
    }

    /**
     * Q&A 게이트: 최고 발췌의 코사인이 minScore 미만이고 키워드 겹침(termCoverage)도
     * keywordMinScore 미만이면 "관련 내용 없음"으로 LLM 호출을 생략한다.
     */
    public record Qa(int contextChunks, double minScore, double keywordMinScore) {
        public Qa {
            if (contextChunks <= 0) contextChunks = 4;
            if (minScore <= 0) minScore = 0.25;
            if (keywordMinScore <= 0 || keywordMinScore > 1) keywordMinScore = 0.5;
        }
    }
}
