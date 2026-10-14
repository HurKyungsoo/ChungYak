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

/**
 * 공고문 청크에 대한 <b>하이브리드</b> 검색 — 의미(벡터 코사인) + 키워드(BM25).
 *
 * 두 순위를 RRF(Reciprocal Rank Fusion)로 섞는다: 점수 스케일이 달라도 순위만 쓰므로 정규화가 필요 없다.
 * 벡터 쪽 가중치는 {@code rag.search.hybrid-weight}.
 * 판정이 아니라 정보 검색이다 — 결과 청크는 근거 인용과 함께 LLM 답변 컨텍스트로 들어간다.
 *
 * 임베딩 클라이언트(Voyage)가 없으면 <b>BM25 모드</b>(#22) — 키워드 순위만 쓴다. 질의 임베딩을 만들 수 없고
 * 청크에도 벡터가 없기 때문이다(AnnouncementIndexer 가 빈 배열로 저장). 이때 {@link Hit#score} 는 0 이다.
 *
 * <b>캐시</b> (#28·#29): BM25 코퍼스 <b>통계</b>(IDF·평균 길이)만 메모리에 두고, 청크 집합이 바뀌었을 때만
 * ({@link DocumentChunkRepository#indexVersion} — 개수·최대 id) 다시 만든다. 질문이 오면 그 공고의 청크만 DB 에서
 * 읽어 즉석으로 점수를 낸다. 전체 청크의 단어 빈도를 들고 있던 때는 5,802 청크에 ~130MB 였고, 공고문 전문
 * (~19,000 청크)이면 ~426MB 라 서버(여유 ~580MB)에 들어가지 않는다.
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

    /** 코퍼스 BM25 통계(IDF·평균 길이)와 그 버전 — 청크 자체는 들고 있지 않는다(#29 메모리) */
    private record Corpus(long count, long maxId, Bm25Index bm25) {}

    private volatile Corpus corpus;

    public VectorSearch(DocumentChunkRepository chunkRepository,
                        Optional<EmbeddingClient> embeddingClient,
                        RagProperties properties) {
        this.chunkRepository = chunkRepository;
        this.embeddingClient = embeddingClient;
        this.defaultTopK = properties.search().topK();
        this.hybridWeight = properties.search().hybridWeight();
    }

    /** 의미(벡터) 검색까지 하는지 — false 면 BM25(키워드)만. 검색 자체는 두 모드 모두 된다. */
    public boolean isSemantic() {
        return embeddingClient.isPresent();
    }

    /**
     * 한 조각의 검색 결과.
     * @param score        의미 유사도(코사인, -1..1). BM25 모드에서는 0
     * @param keywordScore 질의 키워드 겹침 비율(0..1)
     */
    public record Hit(Long announcementId, int chunkIndex, String content,
                      double score, double keywordScore) {}

    public List<Hit> search(String query) {
        return search(query, defaultTopK);
    }

    /** 전체 공고문에서 검색 — 관리자 API 용. 전체 청크를 읽으므로 화면에서는 쓰지 않는다. */
    public List<Hit> search(String query, int topK) {
        return hybrid(query, topK, chunkRepository.findAll());
    }

    /** 특정 공고 안에서만 검색 — "이 공고에 질문하기" 화면용. 그 공고의 청크만 읽고, BM25 IDF 는 전체 코퍼스 기준. */
    public List<Hit> searchWithin(Long announcementId, String query, int topK) {
        if (query == null || query.isBlank()) return List.of();
        return hybrid(query, topK, chunkRepository.findByAnnouncementId(announcementId));
    }

    private List<Hit> hybrid(String query, int topK, List<DocumentChunk> chunks) {
        if (query == null || query.isBlank() || chunks.isEmpty()) {
            return List.of();
        }
        String q = query.strip();
        // 청크에 벡터가 없으면(BM25 모드로 만든 인덱스 — Voyage 를 켠 직후 재인덱싱이 끝나기 전) 이 공고는 BM25 로만 찾는다.
        // 코사인이 전부 0 이라 벡터 순위가 청크 순서가 되어 앞쪽 청크를 끌어올리는 잡음이 된다. 질의 임베딩 호출도 아낀다.
        boolean chunksHaveVectors = chunks.stream().allMatch(c -> c.getEmbedding() != null && c.getEmbedding().length > 0);
        float[] qVec = chunksHaveVectors
                ? embeddingClient.map(e -> e.embed(List.of(q), InputType.QUERY).get(0)).orElse(null) : null;

        // BM25 IDF 는 전체 코퍼스 통계로 (공고 하나로 좁히면 IDF 가 의미 없다), 단어 빈도는 청크마다 즉석으로
        Bm25Index bm25 = corpus().bm25();
        Bm25Index.Query bq = Bm25Index.query(q);

        List<Cand> cands = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk c = chunks.get(i);
            cands.add(new Cand(i, c,
                    qVec == null ? 0.0 : Cosine.similarity(qVec, c.getEmbedding()), bm25.score(bq, c.getContent()),
                    Bm25Index.coverage(bq, c.getContent())));
        }
        if (cands.isEmpty()) return List.of();

        if (qVec == null) {
            // BM25 모드 — 키워드가 하나도 안 걸린 청크는 순위를 매길 근거가 없어 뺀다
            return cands.stream()
                    .filter(x -> x.bm() > 0)
                    .sorted(Comparator.comparingDouble(Cand::bm).reversed())
                    .limit(Math.max(1, topK))
                    .map(x -> new Hit(x.c().getAnnouncementId(), x.c().getChunkIndex(),
                            x.c().getContent(), 0.0, x.cover()))
                    .toList();
        }

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

    /**
     * 캐시된 코퍼스 통계 — 청크 집합의 버전(개수·최대 id)이 그대로면 재사용, 바뀌었으면 본문만 다시 읽어 만든다.
     * 버전을 못 읽으면(테스트 목 등) 매번 새로 만든다.
     */
    private Corpus corpus() {
        DocumentChunkRepository.IndexVersion v = chunkRepository.indexVersion();
        Corpus c = corpus;
        if (v != null && c != null && c.count() == v.getCount() && c.maxId() == v.getMaxId()) return c;
        synchronized (this) {
            c = corpus;
            if (v != null && c != null && c.count() == v.getCount() && c.maxId() == v.getMaxId()) return c;
            List<String> contents = chunkRepository.findAllContents();
            Corpus built = new Corpus(v == null ? -1 : v.getCount(), v == null ? -1 : v.getMaxId(),
                    Bm25Index.statsOf(contents));
            if (v != null) {
                corpus = built;
                log.info("검색 코퍼스 통계 다시 만듦 — 청크 {}개", contents.size());
            }
            return built;
        }
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
