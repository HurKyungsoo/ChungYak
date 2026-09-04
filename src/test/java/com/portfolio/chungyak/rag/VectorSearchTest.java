package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.domain.DocumentChunk;
import com.portfolio.chungyak.rag.embedding.EmbeddingClient;
import com.portfolio.chungyak.repository.DocumentChunkRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VectorSearchTest {

    private final DocumentChunkRepository chunkRepo = mock(DocumentChunkRepository.class);

    /** 질의는 [1,0] 방향으로 임베드하는 가짜 임베더 */
    private final EmbeddingClient embedder = new EmbeddingClient() {
        @Override public List<float[]> embed(List<String> texts, InputType type) {
            return texts.stream().map(t -> new float[]{1f, 0f}).toList();
        }
        @Override public String model() { return "fake"; }
    };

    private final RagProperties props = new RagProperties(
            new RagProperties.Chunk(900, 150), new RagProperties.Search(3), new RagProperties.Qa(4, 0.25));

    private DocumentChunk chunk(long annId, int idx, float[] vec) {
        return new DocumentChunk(annId, idx, "c" + idx, vec, "fake", "h", Instant.now());
    }

    @Test
    @DisplayName("질의 벡터에 가까운 청크가 위로 정렬된다")
    void ranksByCosine() {
        when(chunkRepo.findAll()).thenReturn(List.of(
                chunk(1, 0, new float[]{0f, 1f}),    // 직교 → 0
                chunk(2, 0, new float[]{1f, 0.1f}),  // 거의 같은 방향 → ~1
                chunk(3, 0, new float[]{-1f, 0f})));  // 반대 → -1

        VectorSearch search = new VectorSearch(chunkRepo, Optional.of(embedder), props);
        List<VectorSearch.Hit> hits = search.search("아무 질의", 3);

        assertThat(hits).extracting(VectorSearch.Hit::announcementId)
                .containsExactly(2L, 1L, 3L);
        assertThat(hits.get(0).score()).isGreaterThan(hits.get(1).score());
    }

    @Test
    @DisplayName("topK 로 자른다")
    void limitsTopK() {
        when(chunkRepo.findAll()).thenReturn(List.of(
                chunk(1, 0, new float[]{1f, 0f}), chunk(2, 0, new float[]{0.9f, 0.1f}),
                chunk(3, 0, new float[]{0.5f, 0.5f}), chunk(4, 0, new float[]{0f, 1f})));

        VectorSearch search = new VectorSearch(chunkRepo, Optional.of(embedder), props);
        assertThat(search.search("q", 2)).hasSize(2);
    }

    @Test
    @DisplayName("임베더 없음 / 빈 질의 → 빈 결과")
    void disabledOrBlank() {
        VectorSearch noEmbed = new VectorSearch(chunkRepo, Optional.empty(), props);
        assertThat(noEmbed.search("q")).isEmpty();

        VectorSearch withEmbed = new VectorSearch(chunkRepo, Optional.of(embedder), props);
        assertThat(withEmbed.search("  ")).isEmpty();
    }

    @Test
    @DisplayName("searchWithin — 특정 공고 청크만 대상")
    void searchWithinScopesToAnnouncement() {
        when(chunkRepo.findByAnnouncementId(7L)).thenReturn(List.of(
                chunk(7, 0, new float[]{1f, 0f}), chunk(7, 1, new float[]{0f, 1f})));

        VectorSearch search = new VectorSearch(chunkRepo, Optional.of(embedder), props);
        List<VectorSearch.Hit> hits = search.searchWithin(7L, "q", 5);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).chunkIndex()).isZero();
    }
}
