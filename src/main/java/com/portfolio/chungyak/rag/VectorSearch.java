package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.domain.DocumentChunk;
import com.portfolio.chungyak.rag.embedding.EmbeddingClient;
import com.portfolio.chungyak.rag.embedding.EmbeddingClient.InputType;
import com.portfolio.chungyak.repository.DocumentChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 공고문 청크에 대한 <b>하이브리드</b> 검색 — 의미(벡터 코사인) + 키워드(BM25).
 *
 * 두 순위를 RRF(Reciprocal Rank Fusion)로 섞는다: 점수 스케일이 달라도 순위만 쓰므로 정규화가 필요 없다.
 * 벡터 쪽 가중치는 {@code rag.search.hybrid-weight}.
 * 판정이 아니라 정보 검색이다 — 결과 청크는 근거 인용과 함께 LLM 답변 컨텍스트로 들어간다.
 *
 * 공고 수백 건 규모라 매 질의마다 전체 청크를 메모리에 올려 코사인·BM25 를 계산한다.
 * (수천 건을 넘어가면 역색인/캐시 또는 네이티브 벡터 인덱스로 교체.)
 */
@Slf4j
@Service
public class VectorSearch {

    /** RRF 상수 — 표준값 60. 상위권 순위 차이를 완만하게 반영한다. */
    private static final int RRF_K = 60;

    private final DocumentChunkRepository chunkRepository;
    private final Optional<EmbeddingClient> embeddingClient;
    private final int defaultTopK;
    private final double hybridWeight;

    public VectorSearch(DocumentChunkRepository chunkRepository,
                        Optional<EmbeddingClient> embeddingClient,
                        RagProperties properties) {
        this.chunkRepository = chunkRepository;
        this.embeddingClient = embeddingClient;
        this.defaultTopK = properties.search().topK();
        this.hybridWeight = properties.search().hybridWeight();
    }

    public boolean isAvailable() {
        return embeddingClient.isPresent();
    }

    /**
     * 한 조각의 검색 결과.
     * @param score        의미 유사도(코사인, -1..1)
     * @param keywordScore 질의 키워드 겹침 비율(0..1)
     */
    public record Hit(Long announcementId, int chunkIndex, String content,
                      double score, double keywordScore) {}

    public List<Hit> search(String query) {
        return search(query, defaultTopK);
    }

    /** 전체 공고문에서 검색. */
    public List<Hit> search(String query, int topK) {
        return hybrid(query, topK, c -> true);
    }

    /** 특정 공고 안에서만 검색 — "이 공고에 질문하기" 화면용. BM25 IDF 는 전체 코퍼스 기준. */
    public List<Hit> searchWithin(Long announcementId, String query, int topK) {
        return hybrid(query, topK, c -> c.getAnnouncementId().equals(announcementId));
    }

    private List<Hit> hybrid(String query, int topK, Predicate<DocumentChunk> keep) {
        if (embeddingClient.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        List<DocumentChunk> all = chunkRepository.findAll();
        if (all.isEmpty()) return List.of();

        String q = query.strip();
        float[] qVec = embeddingClient.get().embed(List.of(q), InputType.QUERY).get(0);

        // BM25 는 전체 코퍼스로 (IDF 가 코퍼스 통계라 공고 하나로 좁히면 의미 없다)
        Bm25Index bm25 = Bm25Index.build(all.stream().map(DocumentChunk::getContent).toList());
        double[] bmScores = bm25.scores(q);

        // 후보(필터 통과분)에 코사인·BM25 붙이기
        List<Cand> cands = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            DocumentChunk c = all.get(i);
            if (!keep.test(c)) continue;
            cands.add(new Cand(i, c,
                    Cosine.similarity(qVec, c.getEmbedding()), bmScores[i],
                    bm25.termCoverage(q, i)));
        }
        if (cands.isEmpty()) return List.of();

        Map<Integer, Integer> cosRank = rankMap(cands, Comparator.comparingDouble(Cand::cos).reversed());
        Map<Integer, Integer> bmRank = rankMap(
                cands.stream().filter(x -> x.bm() > 0).toList(),
                Comparator.comparingDouble(Cand::bm).reversed());

        return cands.stream()
                .sorted(Comparator.comparingDouble((Cand x) -> rrf(cosRank.get(x.idx()), bmRank.get(x.idx()))).reversed())
                .limit(Math.max(1, topK))
                .map(x -> new Hit(x.c().getAnnouncementId(), x.c().getChunkIndex(),
                        x.c().getContent(), x.cos(), x.cover()))
                .toList();
    }

    private double rrf(Integer cosRank, Integer bmRank) {
        double s = hybridWeight * 1.0 / (RRF_K + cosRank);
        if (bmRank != null) s += (1 - hybridWeight) * 1.0 / (RRF_K + bmRank);
        return s;
    }

    private static Map<Integer, Integer> rankMap(List<Cand> list, Comparator<Cand> order) {
        List<Cand> sorted = list.stream().sorted(order).toList();
        Map<Integer, Integer> ranks = new HashMap<>();
        for (int r = 0; r < sorted.size(); r++) ranks.put(sorted.get(r).idx(), r + 1);
        return ranks;
    }

    private record Cand(int idx, DocumentChunk c, double cos, double bm, double cover) {}
}
