package com.portfolio.chungyak.rag;

/**
 * {@link AnnouncementIndexer#indexPending()} 결과 요약.
 *
 * @param model 이번에 쓴 인덱스 모델 — 임베딩 모델명, 또는 BM25 모드면 {@link AnnouncementIndexer#BM25_MODEL}
 */
public record RagIndexReport(String model, int docsScanned, int docsIndexed,
                             int docsSkipped, int docsFailed, int chunksCreated) {
}
