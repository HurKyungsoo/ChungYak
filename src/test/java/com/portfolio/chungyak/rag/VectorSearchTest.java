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

/**
 * 하이브리드 검색 — 벡터(코사인) + BM25(키워드)를 RRF 로 융합.
 */
class VectorSearchTest {

    private final DocumentChunkRepository chunkRepo = mock(DocumentChunkRepository.class);

    /** 질의는 항상 [1,0] 방향으로 임베드하는 가짜 임베더 */
    private final EmbeddingClient embedder = new EmbeddingClient() {
        @Override public List<float[]> embed(List<String> texts, InputType type) {
            return texts.stream().map(t -> new float[]{1f, 0f}).toList();
        }
        @Override public String model() { return "fake"; }
    };

    /** 저장소 목 — 전체 조회·본문만 조회·공고별 조회가 같은 청크 집합을 보게 */
    private void stub(List<DocumentChunk> chunks) {
        when(chunkRepo.findAll()).thenReturn(chunks);
        when(chunkRepo.findAllContents()).thenReturn(chunks.stream().map(DocumentChunk::getContent).toList());
        when(chunkRepo.findByAnnouncementId(org.mockito.ArgumentMatchers.anyLong())).thenAnswer(inv ->
                chunks.stream().filter(c -> c.getAnnouncementId().equals(inv.getArgument(0))).toList());
    }

    private RagProperties props(double hybridWeight) {
        return new RagProperties(new RagProperties.Chunk(900, 150),
                new RagProperties.Search(5, hybridWeight), new RagProperties.Qa(4, 0.25, 0.5));
    }

    private DocumentChunk chunk(long annId, int idx, float[] vec, String content) {
        return new DocumentChunk(annId, idx, content, vec, "fake", "h", Instant.now());
    }

    @Test
    @DisplayName("의미가 가까운 청크가 위로 (벡터 신호)")
    void ranksBySemantics() {
        stub(List.of(
                chunk(1, 0, new float[]{0f, 1f}, "관련 없는 내용"),
                chunk(2, 0, new float[]{1f, 0.1f}, "거의 같은 방향"),
                chunk(3, 0, new float[]{-1f, 0f}, "반대 방향")));

        VectorSearch search = new VectorSearch(chunkRepo, Optional.of(embedder), props(0.6));
        List<VectorSearch.Hit> hits = search.search("아무 질의", 3);

        assertThat(hits.get(0).announcementId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("검색에 쓴 질의 임베딩을 기억한다 — 비슷한 질문 재사용(#45)과 같은 질문 재검색이 Voyage 를 다시 부르지 않는다")
    void remembersQueryVector() {
        stub(List.of(chunk(1, 0, new float[]{1f, 0f}, "계약금 10%")));
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        EmbeddingClient counting = new EmbeddingClient() {
            @Override public List<float[]> embed(List<String> texts, InputType type) {
                calls.incrementAndGet();
                return embedder.embed(texts, type);
            }
            @Override public String model() { return "fake"; }
        };
        VectorSearch search = new VectorSearch(chunkRepo, Optional.of(counting), props(0.4));

        assertThat(search.cachedQueryVector("계약금은?")).isEmpty();
        search.searchWithin(1L, "계약금은?", 3);
        search.searchWithin(1L, " 계약금은? ", 3);

        assertThat(calls).hasValue(1);
        assertThat(search.cachedQueryVector("계약금은?")).hasValueSatisfying(v -> assertThat(v).containsExactly(1f, 0f));
    }

    @Test
    @DisplayName("벡터로는 안 잡히는 청크도 키워드가 정확히 맞으면 상위로 (BM25 신호)")
    void keywordLiftsExactMatch() {
        // 벡터상 전부 직교(코사인 0). 순위는 BM25 가 가른다.
        stub(List.of(
                chunk(1, 0, new float[]{0f, 1f}, "일반적인 청약 안내문입니다"),
                chunk(2, 1, new float[]{0f, 1f}, "발코니 확장 비용은 세대당 별도 부과됩니다"),
                chunk(3, 2, new float[]{0f, 1f}, "당첨자 발표는 홈페이지에서 확인하세요")));

        VectorSearch search = new VectorSearch(chunkRepo, Optional.of(embedder), props(0.5));
        List<VectorSearch.Hit> hits = search.search("발코니 확장 비용", 3);

        assertThat(hits.get(0).chunkIndex()).isEqualTo(1);          // "발코니 확장 비용" 청크
        assertThat(hits.get(0).keywordScore()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("keywordScore 는 질의 키워드 겹침 비율")
    void keywordScoreIsCoverage() {
        stub(List.of(
                chunk(1, 0, new float[]{1f, 0f}, "잔여세대 신청은 무주택자만 가능합니다"),
                chunk(1, 1, new float[]{1f, 0f}, "관계 없는 문장")));

        VectorSearch search = new VectorSearch(chunkRepo, Optional.of(embedder), props(0.6));
        List<VectorSearch.Hit> hits = search.search("잔여세대 신청", 2);

        VectorSearch.Hit matched = hits.stream().filter(h -> h.chunkIndex() == 0).findFirst().orElseThrow();
        VectorSearch.Hit other = hits.stream().filter(h -> h.chunkIndex() == 1).findFirst().orElseThrow();
        assertThat(matched.keywordScore()).isGreaterThan(other.keywordScore());
    }

    @Test
    @DisplayName("searchWithin — 그 공고 청크만, BM25 IDF 는 전체 코퍼스 기준")
    void searchWithinScopesButUsesGlobalIdf() {
        stub(List.of(
                chunk(7, 0, new float[]{1f, 0f}, "7번 공고: 특별공급 일정 안내"),
                chunk(7, 1, new float[]{0f, 1f}, "7번 공고: 잔여세대 신청 조건"),
                chunk(9, 0, new float[]{1f, 0f}, "9번 공고: 잔여세대 잔여세대 잔여세대")));

        VectorSearch search = new VectorSearch(chunkRepo, Optional.of(embedder), props(0.4));
        List<VectorSearch.Hit> hits = search.searchWithin(7L, "잔여세대 신청 조건", 5);

        assertThat(hits).extracting(VectorSearch.Hit::announcementId).containsOnly(7L);
        assertThat(hits.get(0).chunkIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("topK 로 자른다")
    void limitsTopK() {
        stub(List.of(
                chunk(1, 0, new float[]{1f, 0f}, "a"), chunk(2, 0, new float[]{0.9f, 0.1f}, "b"),
                chunk(3, 0, new float[]{0.5f, 0.5f}, "c"), chunk(4, 0, new float[]{0f, 1f}, "d")));

        VectorSearch search = new VectorSearch(chunkRepo, Optional.of(embedder), props(0.6));
        assertThat(search.search("q", 2)).hasSize(2);
    }

    @Test
    @DisplayName("임베더 없음 / 빈 질의 / 코퍼스 없음 → 빈 결과")
    void degenerate() {
        VectorSearch noEmbed = new VectorSearch(chunkRepo, Optional.empty(), props(0.6));
        assertThat(noEmbed.search("q")).isEmpty();

        VectorSearch withEmbed = new VectorSearch(chunkRepo, Optional.of(embedder), props(0.6));
        assertThat(withEmbed.search("  ")).isEmpty();

        stub(List.of());
        assertThat(withEmbed.search("q")).isEmpty();
    }

    @Test
    @DisplayName("임베더 없음 → BM25 모드: 키워드 순위만, 의미 점수 0, 키워드가 안 걸린 청크는 제외 (#22)")
    void bm25OnlyWithoutEmbedder() {
        stub(List.of(
                chunk(1, 0, new float[0], "일반적인 청약 안내문입니다"),
                chunk(1, 1, new float[0], "발코니 확장 비용은 세대당 별도 부과됩니다"),
                chunk(1, 2, new float[0], "당첨자 발표는 홈페이지에서 확인하세요")));

        VectorSearch search = new VectorSearch(chunkRepo, Optional.empty(), props(0.6));
        List<VectorSearch.Hit> hits = search.searchWithin(1L, "발코니 확장 비용", 3);

        assertThat(search.isSemantic()).isFalse();
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).chunkIndex()).isEqualTo(1);
        assertThat(hits).allSatisfy(h -> {
            assertThat(h.score()).isZero();
            assertThat(h.keywordScore()).isPositive();
        });
    }

    private static DocumentChunkRepository.IndexVersion version(long count, long maxId) {
        return new DocumentChunkRepository.IndexVersion() {
            @Override public long getCount() { return count; }
            @Override public long getMaxId() { return maxId; }
        };
    }

    @Test
    @DisplayName("코퍼스 통계 캐시 — 청크 버전이 같으면 본문을 다시 읽지 않고, 바뀌면 다시 만든다 (#28·#29)")
    void cachesCorpusUntilVersionChanges() {
        stub(List.of(chunk(1, 0, new float[0], "발코니 확장 비용 안내")));
        when(chunkRepo.indexVersion()).thenReturn(version(1, 10));
        VectorSearch search = new VectorSearch(chunkRepo, Optional.empty(), props(0.6));

        search.searchWithin(1L, "발코니 확장", 3);
        search.searchWithin(1L, "확장 비용", 3);
        org.mockito.Mockito.verify(chunkRepo, org.mockito.Mockito.times(1)).findAllContents();

        // 재인덱싱 — 지우고 다시 넣어 최대 id 가 바뀐다
        stub(List.of(chunk(1, 0, new float[0], "계약금 정액제 안내")));
        when(chunkRepo.indexVersion()).thenReturn(version(1, 11));
        List<VectorSearch.Hit> hits = search.searchWithin(1L, "계약금", 3);

        org.mockito.Mockito.verify(chunkRepo, org.mockito.Mockito.times(2)).findAllContents();
        assertThat(hits).extracting(VectorSearch.Hit::content).containsExactly("계약금 정액제 안내");
    }

    @Test
    @DisplayName("청크에 벡터가 없으면(Voyage 를 켠 직후 재인덱싱 전) BM25 로 — 질의 임베딩도 부르지 않는다")
    void fallsBackToBm25WhenChunksHaveNoVectors() {
        stub(List.of(
                chunk(1, 0, new float[0], "앞쪽 청크 관련 없는 내용"),
                chunk(1, 1, new float[0], "견본주택 주소 안내")));
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        EmbeddingClient counting = new EmbeddingClient() {
            @Override public List<float[]> embed(List<String> texts, InputType type) {
                calls.incrementAndGet();
                return texts.stream().map(t -> new float[]{1f, 0f}).toList();
            }
            @Override public String model() { return "fake"; }
        };

        List<VectorSearch.Hit> hits = new VectorSearch(chunkRepo, Optional.of(counting), props(0.4))
                .searchWithin(1L, "견본주택 주소", 2);

        assertThat(hits).extracting(VectorSearch.Hit::chunkIndex).containsExactly(1);   // 키워드 안 걸린 청크는 빠진다
        assertThat(calls).hasValue(0);
    }
}
