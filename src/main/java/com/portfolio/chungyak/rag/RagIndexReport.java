package com.portfolio.chungyak.rag;

/**
 * {@link AnnouncementIndexer#indexPending()} 결과 요약.
 */
public record RagIndexReport(boolean enabled, int docsScanned, int docsIndexed,
                             int docsSkipped, int docsFailed, int chunksCreated) {

    public static RagIndexReport disabled() {
        return new RagIndexReport(false, 0, 0, 0, 0, 0);
    }
}
