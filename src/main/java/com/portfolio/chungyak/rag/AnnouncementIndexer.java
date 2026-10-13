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
 * 다시 인덱싱한다(idempotent).
 *
 * <b>두 모드</b> (#22): {@link EmbeddingClient}(Voyage) 가 있으면 청크 + 임베딩, 없으면 <b>BM25 모드</b> —
 * 청크만 저장하고 임베딩은 빈 배열({@code "[]"}), 모델명은 {@link #BM25_MODEL}. 스키마는 그대로다
 * ({@code embedding} 이 NOT NULL). 나중에 Voyage 키를 넣으면 모델명 불일치로 {@link #isUpToDate} 가
 * 낡은 것으로 보고 임베딩까지 다시 만든다.
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

    /** 임베딩 없이 청크만 저장한 인덱스의 모델명 — 검색은 BM25(키워드)만 쓴다. */
    public static final String BM25_MODEL = "bm25";

    /** 의미(벡터) 검색까지 되는지 — false 면 BM25 모드 */
    public boolean isSemantic() {
        return embeddingClient.isPresent();
    }

    /**
     * 수집 직후 자동 인덱싱 — <b>BM25 모드일 때만</b>(무료). 임베딩 모드는 호출마다 과금이라
     * 지금처럼 관리자 API({@code /api/admin/rag/reindex})로만 돌린다.
     */
    public void indexAfterSync() {
        if (isSemantic()) {
            log.info("임베딩 모드 — 수집 후 자동 인덱싱은 하지 않음(과금). 필요하면 /api/admin/rag/reindex");
            return;
        }
        indexPending();
    }

    /** 원문이 있으나 최신 인덱스가 없는 공고를 모두 인덱싱한다. */
    public RagIndexReport indexPending() {
        String model = embeddingClient.map(EmbeddingClient::model).orElse(BM25_MODEL);

        int scanned = 0, indexed = 0, skipped = 0, failed = 0, chunks = 0;
        for (AnnouncementDocument doc : documentRepository.findAll()) {
            scanned++;
            if (isUpToDate(doc, model)) {
                skipped++;
                continue;
            }
            try {
                chunks += reindex(doc, model);
                indexed++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("공고 #{} 인덱싱 실패 — {}", doc.getAnnouncementId(), e.toString());
            }
        }

        RagIndexReport report = new RagIndexReport(model, scanned, indexed, skipped, failed, chunks);
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
    private int reindex(AnnouncementDocument doc, String model) {
        chunkRepository.deleteByAnnouncementId(doc.getAnnouncementId());

        List<String> texts = splitter.split(doc.getRawText());
        if (texts.isEmpty()) {
            log.info("공고 #{} 원문에서 청크가 나오지 않음", doc.getAnnouncementId());
            return 0;
        }

        List<float[]> vectors = embeddingClient
                .map(client -> client.embed(texts, InputType.DOCUMENT))
                .orElseGet(() -> texts.stream().map(t -> new float[0]).toList());
        if (vectors.size() != texts.size()) {
            throw new IllegalStateException("임베딩 개수 불일치: " + vectors.size() + " != " + texts.size());
        }

        Instant now = clock.instant();
        for (int i = 0; i < texts.size(); i++) {
            chunkRepository.save(new DocumentChunk(
                    doc.getAnnouncementId(), i, texts.get(i), vectors.get(i),
                    model, doc.getTextHash(), now));
        }
        return texts.size();
    }
}
