package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.domain.AnnouncementDocument;
import com.portfolio.chungyak.domain.DocumentChunk;
import com.portfolio.chungyak.rag.embedding.EmbeddingClient;
import com.portfolio.chungyak.repository.AnnouncementDocumentRepository;
import com.portfolio.chungyak.repository.DocumentChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 인덱서 오케스트레이션 — 실제 DB·Voyage 없이 검증.
 * 재인덱싱 판단(원문 해시·모델), idempotency, 실패 격리.
 */
class AnnouncementIndexerTest {

    private AnnouncementDocumentRepository docRepo;
    private DocumentChunkRepository chunkRepo;
    private AtomicInteger embedCalls;
    private AnnouncementIndexer indexer;

    /** 텍스트마다 [len, 1] 벡터를 주는 가짜 임베더 */
    private EmbeddingClient fakeEmbedder(String model) {
        return new EmbeddingClient() {
            @Override public List<float[]> embed(List<String> texts, InputType type) {
                embedCalls.incrementAndGet();
                return texts.stream().map(t -> new float[]{t.length(), 1f}).toList();
            }
            @Override public String model() { return model; }
        };
    }

    @BeforeEach
    void setUp() {
        docRepo = mock(AnnouncementDocumentRepository.class);
        chunkRepo = mock(DocumentChunkRepository.class);
        embedCalls = new AtomicInteger();
        Clock clock = Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC);
        indexer = new AnnouncementIndexer(docRepo, chunkRepo, new ChunkSplitter(120, 20),
                Optional.of(fakeEmbedder("voyage-4-lite")), clock);
    }

    private AnnouncementDocument doc(long annId, String text) {
        return new AnnouncementDocument(annId, "LH", text, Instant.parse("2026-09-04T00:00:00Z"));
    }

    @Test
    @DisplayName("임베더 없음 → disabled, 아무것도 안 함")
    void disabledWithoutEmbedder() {
        AnnouncementIndexer noEmbed = new AnnouncementIndexer(docRepo, chunkRepo,
                new ChunkSplitter(120, 20), Optional.empty(), Clock.systemUTC());

        RagIndexReport report = noEmbed.indexPending();

        assertThat(report.enabled()).isFalse();
        verifyNoInteractions(docRepo);
    }

    @Test
    @DisplayName("청크가 없는 공고 → 분할·임베딩·저장")
    void indexesFreshDocument() {
        AnnouncementDocument d = doc(1L, "잔여세대 신청 조건 안내. ".repeat(40));
        when(docRepo.findAll()).thenReturn(List.of(d));
        when(chunkRepo.findByAnnouncementId(1L)).thenReturn(List.of());

        RagIndexReport report = indexer.indexPending();

        assertThat(report.docsIndexed()).isEqualTo(1);
        assertThat(report.chunksCreated()).isGreaterThan(1);
        verify(chunkRepo).deleteByAnnouncementId(1L);
        verify(chunkRepo, times(report.chunksCreated())).save(any(DocumentChunk.class));
    }

    @Test
    @DisplayName("이미 최신 인덱스가 있으면 건너뛴다 (해시·모델 일치)")
    void skipsUpToDate() {
        AnnouncementDocument d = doc(1L, "짧은 공고문 내용입니다.");
        when(docRepo.findAll()).thenReturn(List.of(d));
        when(chunkRepo.findByAnnouncementId(1L)).thenReturn(List.of(
                new DocumentChunk(1L, 0, "짧은 공고문 내용입니다.", new float[]{1, 1},
                        "voyage-4-lite", d.getTextHash(), Instant.now())));

        RagIndexReport report = indexer.indexPending();

        assertThat(report.docsSkipped()).isEqualTo(1);
        assertThat(report.docsIndexed()).isZero();
        verify(chunkRepo, never()).save(any());
        assertThat(embedCalls).hasValue(0);
    }

    @Test
    @DisplayName("원문이 바뀌면 (해시 불일치) 다시 인덱싱한다")
    void reindexesOnTextChange() {
        AnnouncementDocument d = doc(1L, "새 공고문 내용. 조건이 바뀌었습니다.");
        when(docRepo.findAll()).thenReturn(List.of(d));
        when(chunkRepo.findByAnnouncementId(1L)).thenReturn(List.of(
                new DocumentChunk(1L, 0, "옛 내용", new float[]{1, 1},
                        "voyage-4-lite", "old-hash", Instant.now())));

        RagIndexReport report = indexer.indexPending();

        assertThat(report.docsIndexed()).isEqualTo(1);
        verify(chunkRepo).deleteByAnnouncementId(1L);
    }

    @Test
    @DisplayName("임베딩 모델이 바뀌면 다시 인덱싱한다")
    void reindexesOnModelChange() {
        AnnouncementDocument d = doc(1L, "공고문 내용입니다.");
        when(docRepo.findAll()).thenReturn(List.of(d));
        when(chunkRepo.findByAnnouncementId(1L)).thenReturn(List.of(
                new DocumentChunk(1L, 0, "공고문 내용입니다.", new float[]{1, 1},
                        "voyage-2", d.getTextHash(), Instant.now())));

        assertThat(indexer.indexPending().docsIndexed()).isEqualTo(1);
    }

    @Test
    @DisplayName("한 공고 임베딩이 터져도 나머지는 계속 인덱싱한다")
    void failureIsIsolated() {
        AnnouncementIndexer flaky = new AnnouncementIndexer(docRepo, chunkRepo,
                new ChunkSplitter(120, 20), Optional.of(new EmbeddingClient() {
            @Override public List<float[]> embed(List<String> texts, InputType type) {
                if (texts.get(0).contains("BOOM")) throw new RuntimeException("503");
                return texts.stream().map(t -> new float[]{1f, 0f}).toList();
            }
            @Override public String model() { return "voyage-4-lite"; }
        }), Clock.systemUTC());

        when(docRepo.findAll()).thenReturn(List.of(doc(1L, "BOOM 폭발"), doc(2L, "정상 공고문")));
        when(chunkRepo.findByAnnouncementId(anyLong())).thenReturn(List.of());

        RagIndexReport report = flaky.indexPending();

        assertThat(report.docsFailed()).isEqualTo(1);
        assertThat(report.docsIndexed()).isEqualTo(1);
    }
}
