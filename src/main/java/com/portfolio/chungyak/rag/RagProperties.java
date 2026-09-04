package com.portfolio.chungyak.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 인덱싱·검색 파라미터 (rag.*). 코드에 박지 않고 배포 없이 조정한다.
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(Chunk chunk, Search search) {

    public RagProperties {
        if (chunk == null) chunk = new Chunk(0, 0);
        if (search == null) search = new Search(0);
    }

    /** 청크 목표 길이·겹침 (문자 수). */
    public record Chunk(int size, int overlap) {
        public Chunk {
            if (size <= 0) size = 900;
            if (overlap < 0 || overlap >= size) overlap = Math.min(150, size / 4);
        }
    }

    public record Search(int topK) {
        public Search {
            if (topK <= 0) topK = 5;
        }
    }
}
