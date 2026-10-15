package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.rag.embedding.EmbeddingClient;
import com.portfolio.chungyak.rag.embedding.EmbeddingClient.InputType;
import com.portfolio.chungyak.rag.embedding.Reranker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * 이용자 질문 → 비슷한 국토부 FAQ 문항(공식 해석). 답은 원문 그대로 보여 준다 — LLM 을 거치지 않는다.
 *
 * 방식(2026-10-15 {@code OfficialFaqEvalTest}, 이용자 말투 질문): FAQ 질문 제목에 BM25 + 임베딩 순위를 RRF 로 섞어
 * 상위 20개 → Voyage rerank(제목 + 답 앞 400자) → 1위 점수가 {@code rag.faq.min-score}(0.60) 이상일 때만.
 * <ul>
 *   <li>dev(26문항·부정 6): 1위 22/26, 3위 안 24/26. 부정(견본주택·계약금 등 공고마다 다른 질문) 최고 0.523,
 *       정답 최저 0.740 → 기준 0.60</li>
 *   <li>test(25문항·부정 6, 방식·기준을 정한 뒤 한 번): 1위 22/25, 3위 안 25/25. <b>부정 1건이 기준을 넘었다</b> —
 *       "동호수 추첨은 언제 해요?" 0.617 → Q385(예비입주자 동호수 추첨일과 다른 단지 당첨일이 겹칠 때). 기준을 test 로
 *       고치지 않았다. 화면은 공고문 답을 대신하지 않고 "비슷한 질문의 국토부 답"으로 FAQ 질문 제목과 함께 보여 줘
 *       이용자가 맞는 질문인지 볼 수 있게 한다.</li>
 * </ul>
 * BM25 단독(1위 15/26)이나 벡터 단독은 기준값을 검증하지 않았다 — 재정렬기(Voyage)가 없으면 이 기능은 꺼진다.
 */
@Slf4j
@Service
public class OfficialFaqSearch {

    private static final int CANDIDATES = 20;
    private static final int RRF_K = 60;
    private static final int RELATED = 2;

    private final OfficialFaq faq;
    private final Optional<EmbeddingClient> embeddings;
    private final Optional<Reranker> reranker;
    private final double minScore;
    private final List<String> titles;
    private final Bm25Index bm25;
    private volatile List<float[]> titleVectors;

    /**
     * @param item       가장 가까운 FAQ 문항
     * @param related    그다음 문항들(기준 이상만) — 1위가 조금 빗나갔을 때 이용자가 고를 수 있게
     * @param amendments 기준일(2024-05-29) 뒤 이 문항과 겹치는 개정
     */
    public record Match(OfficialFaq.Item item, double score, List<OfficialFaq.Item> related,
                        List<OfficialFaq.Amendment> amendments, String source, String basisDate, String url) {}

    @Autowired
    public OfficialFaqSearch(Optional<EmbeddingClient> embeddings, Optional<Reranker> reranker,
                             @Value("${rag.faq.min-score:0.60}") double minScore) {
        this(OfficialFaq.load(), embeddings, reranker, minScore);
    }

    OfficialFaqSearch(OfficialFaq faq, Optional<EmbeddingClient> embeddings, Optional<Reranker> reranker, double minScore) {
        this.faq = faq;
        this.embeddings = embeddings;
        this.reranker = reranker;
        this.minScore = minScore;
        this.titles = faq.items().stream().map(OfficialFaq.Item::question).toList();
        this.bm25 = Bm25Index.statsOf(titles);
    }

    public boolean isEnabled() {
        return reranker.isPresent();
    }

    public Optional<Match> search(String question) {
        if (!isEnabled() || question == null || question.isBlank()) return Optional.empty();
        String q = question.strip();
        try {
            List<Integer> cand = candidates(q);
            List<OfficialFaq.Item> items = faq.items();
            double[] score = reranker.get().scores(q, cand.stream().map(i -> rerankDoc(items.get(i))).toList());
            List<Integer> order = IntStream.range(0, cand.size()).boxed()
                    .sorted(Comparator.comparingDouble((Integer k) -> score[k]).reversed()).toList();
            double best = score[order.get(0)];
            if (best < minScore) {
                log.debug("공식 FAQ — 최고 점수 {} 기준 미달", String.format("%.3f", best));
                return Optional.empty();
            }
            OfficialFaq.Item top = items.get(cand.get(order.get(0)));
            List<OfficialFaq.Item> related = order.stream().skip(1).limit(RELATED)
                    .filter(k -> score[k] >= minScore).map(k -> items.get(cand.get(k))).toList();
            return Optional.of(new Match(top, best, related, faq.amendmentsFor(top), faq.source(), faq.basisDate(), faq.url()));
        } catch (RuntimeException e) {
            log.warn("공식 FAQ 검색 실패 — 건너뜀: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** BM25(제목) + 임베딩(제목) RRF 상위 후보. 임베딩을 못 쓰면 BM25 순서만 */
    private List<Integer> candidates(String q) {
        Bm25Index.Query bq = Bm25Index.query(q);
        int n = titles.size();
        double[] bm = new double[n];
        for (int i = 0; i < n; i++) bm[i] = bm25.score(bq, titles.get(i));
        Map<Integer, Integer> bmRank = rankOf(n, i -> bm[i]);

        List<float[]> vecs = titleVectors();
        if (vecs == null) {
            return IntStream.range(0, n).boxed().sorted(Comparator.comparing(bmRank::get)).limit(CANDIDATES).toList();
        }
        float[] qv = embeddings.get().embed(List.of(q), InputType.QUERY).get(0);
        double[] cos = new double[n];
        for (int i = 0; i < n; i++) cos[i] = Cosine.similarity(qv, vecs.get(i));
        Map<Integer, Integer> vecRank = rankOf(n, i -> cos[i]);
        return IntStream.range(0, n).boxed()
                .sorted(Comparator.comparingDouble((Integer i) ->
                        0.5 / (RRF_K + bmRank.get(i)) + 0.5 / (RRF_K + vecRank.get(i))).reversed())
                .limit(CANDIDATES).toList();
    }

    /** FAQ 제목 480개 임베딩 — 처음 쓸 때 한 번(약 1.4만 토큰). 실패하면 다음에 다시 */
    private List<float[]> titleVectors() {
        if (embeddings.isEmpty()) return null;
        List<float[]> v = titleVectors;
        if (v != null) return v;
        synchronized (this) {
            if (titleVectors == null) {
                List<float[]> out = new ArrayList<>();
                for (int from = 0; from < titles.size(); from += 64) {
                    out.addAll(embeddings.get().embed(titles.subList(from, Math.min(titles.size(), from + 64)), InputType.DOCUMENT));
                }
                titleVectors = List.copyOf(out);
                log.info("공식 FAQ 제목 임베딩 {}개", out.size());
            }
            return titleVectors;
        }
    }

    static String rerankDoc(OfficialFaq.Item it) {
        String a = it.answer();
        return it.question() + "\n" + (a.length() > 400 ? a.substring(0, 400) : a);
    }

    private static Map<Integer, Integer> rankOf(int n, java.util.function.IntToDoubleFunction score) {
        List<Integer> sorted = IntStream.range(0, n).boxed()
                .sorted(Comparator.comparingDouble((Integer i) -> score.applyAsDouble(i)).reversed()).toList();
        Map<Integer, Integer> rank = new HashMap<>();
        for (int r = 0; r < sorted.size(); r++) rank.put(sorted.get(r), r + 1);
        return rank;
    }
}
