package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.domain.AnnouncementDocument;
import com.portfolio.chungyak.domain.DocumentChunk;
import com.portfolio.chungyak.rag.embedding.EmbeddingClient;
import com.portfolio.chungyak.rag.embedding.EmbeddingClient.InputType;
import com.portfolio.chungyak.repository.AnnouncementDocumentRepository;
import com.portfolio.chungyak.repository.DocumentChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 공고문 원문 → 청크 + 임베딩.
 *
 * 판정과 무관하다 — 정보 검색용이다. 원문(textHash)이나 임베딩 모델이 바뀐 공고만
 * 다시 인덱싱한다(idempotent). {@link EmbeddingClient} 가 없으면(키 미설정) 아무것도 안 한다.
 */
@Slf4j
@Service
public class AnnouncementIndexer {

    private final AnnouncementDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ChunkSplitter splitter;
    private final Optional<EmbeddingClient> embeddingClient;
    private final Clock clock;

    public AnnouncementIndexer(AnnouncementDocumentRepository documentRepository,
                               DocumentChunkRepository chunkRepository,
                               ChunkSplitter splitter,
                               Optional<EmbeddingClient> embeddingClient,
                               Clock clock) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.splitter = splitter;
        this.embeddingClient = embeddingClient;
        this.clock = clock;
    }

    public boolean isAvailable() {
        return embeddingClient.isPresent();
    }

    /** 원문이 있으나 최신 인덱스가 없는 공고를 모두 인덱싱한다. */
    public RagIndexReport indexPending() {
        if (embeddingClient.isEmpty()) {
            log.info("임베딩 클라이언트 없음 — RAG 인덱싱 건너뜀");
            return RagIndexReport.disabled();
        }
        EmbeddingClient client = embeddingClient.get();

        int scanned = 0, indexed = 0, skipped = 0, failed = 0, chunks = 0;
        for (AnnouncementDocument doc : documentRepository.findAll()) {
            scanned++;
            if (isUpToDate(doc, client.model())) {
                skipped++;
                continue;
            }
            try {
                chunks += reindex(doc, client);
                indexed++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("공고 #{} 인덱싱 실패 — {}", doc.getAnnouncementId(), e.toString());
            }
        }

        RagIndexReport report = new RagIndexReport(true, scanned, indexed, skipped, failed, chunks);
        log.info("RAG 인덱싱 완료 — {}", report);
        return report;
    }

    private boolean isUpToDate(AnnouncementDocument doc, String model) {
        List<DocumentChunk> existing = chunkRepository.findByAnnouncementId(doc.getAnnouncementId());
        return !existing.isEmpty()
                && existing.stream().allMatch(c ->
                        c.getSourceTextHash().equals(doc.getTextHash())
                        && c.getEmbeddingModel().equals(model));
    }

    /**
     * 원자성보다 재실행 가능성에 기댄다 — 중간에 실패해 청크가 섞여 남아도
     * 다음 실행의 {@link #isUpToDate} 가 불일치로 보고 지운 뒤 다시 만든다.
     */
    private int reindex(AnnouncementDocument doc, EmbeddingClient client) {
        chunkRepository.deleteByAnnouncementId(doc.getAnnouncementId());

        List<String> texts = splitter.split(doc.getRawText());
        if (texts.isEmpty()) {
            log.info("공고 #{} 원문에서 청크가 나오지 않음", doc.getAnnouncementId());
            return 0;
        }

        List<float[]> vectors = client.embed(texts, InputType.DOCUMENT);
        if (vectors.size() != texts.size()) {
            throw new IllegalStateException("임베딩 개수 불일치: " + vectors.size() + " != " + texts.size());
        }

        Instant now = clock.instant();
        for (int i = 0; i < texts.size(); i++) {
            chunkRepository.save(new DocumentChunk(
                    doc.getAnnouncementId(), i, texts.get(i), vectors.get(i),
                    client.model(), doc.getTextHash(), now));
        }
        return texts.size();
    }
}
