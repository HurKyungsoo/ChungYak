package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.rag.embedding.EmbeddingClient;
import com.portfolio.chungyak.rag.embedding.EmbeddingClient.InputType;
import com.portfolio.chungyak.rag.embedding.VoyageEmbeddingClient;
import com.portfolio.chungyak.rag.embedding.VoyageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 공식 FAQ 검색 eval — 이용자 말투 질문({@code faq-colloquial-cases.tsv})이 맞는 국토부 FAQ 문항을 찾는지,
 * 공고마다 다른 값을 묻는 질문(견본주택·계약금…)은 FAQ 에 걸리지 않는지. Voyage 키가 있을 때만(임베딩·rerank 캐시).
 * dev 로 방식·기준값을 정하고 test 는 그 뒤에 한 번 잰다.
 */
@EnabledIf("com.portfolio.chungyak.rag.NoticeQaSemanticEvalTest#keyPresent")
class OfficialFaqEvalTest {

    record Case(String set, Set<Integer> expected, String question) {
        boolean negative() { return expected.isEmpty(); }
    }

    static List<Case> readCases() throws IOException {
        List<Case> out = new ArrayList<>();
        try (var in = OfficialFaqEvalTest.class.getResourceAsStream("/eval/faq-colloquial-cases.tsv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.split("\t");
                Set<Integer> exp = f[1].equals("-") ? Set.of()
                        : Arrays.stream(f[1].split(",")).map(String::trim).map(Integer::valueOf).collect(Collectors.toSet());
                out.add(new Case(f[0], exp, f[2]));
            }
        }
        return out;
    }

    static final OfficialFaq FAQ = OfficialFaq.load();

    private static EmbeddingClient embeddings() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return new NoticeQaSemanticEvalTest.CachingEmbeddings(new VoyageEmbeddingClient(
                RestClient.builder().requestFactory(factory).build(),
                new VoyageProperties(NoticeQaSemanticEvalTest.apiKey(), "voyage-4-lite", null, 64)));
    }

    static String rerankDoc(OfficialFaq.Item it) {
        String a = it.answer();
        return it.question() + "\n" + (a.length() > 400 ? a.substring(0, 400) : a);
    }

    /** 순위 매기기 결과: FAQ 항목 인덱스(점수 높은 순) */
    private static List<Integer> rank(int n, ToDoubleFunction<Integer> score) {
        return IntStream.range(0, n).boxed()
                .sorted(Comparator.comparingDouble((Integer i) -> score.applyAsDouble(i)).reversed()).toList();
    }

    @Test
    @DisplayName("dev — BM25(제목)·BM25(제목+답)·벡터·하이브리드·rerank 비교, rerank 점수 분포")
    void dev() throws IOException {
        report("dev");
    }

    /**
     * test — dev 로 방식(하이브리드 → rerank)·기준값(0.60, dev 부정 최고 0.523)을 정한 뒤 처음 잰 결과(2026-10-15):
     * 기록은 {@code OfficialFaqSearch} 주석. 이 결과를 보고 방식·기준값을 고치지 않는다.
     */
    @Test
    @DisplayName("test — 정한 방식·기준값 그대로")
    void test() throws IOException {
        report("test");
    }

    @Test
    @DisplayName("운영 클래스(OfficialFaqSearch)가 eval 과 같은 결과 — dev·test 기준 통과·1위·부정 오탐")
    void productionPath() throws IOException {
        com.portfolio.chungyak.rag.embedding.Reranker cachedRerank = new com.portfolio.chungyak.rag.embedding.Reranker() {
            @Override public List<Integer> order(String query, List<String> documents) { throw new UnsupportedOperationException(); }
            @Override public double[] scores(String query, List<String> documents) {
                return NoticeQaSemanticEvalTest.rerankScores(query, documents);
            }
            @Override public int candidates() { return 20; }
            @Override public String model() { return "rerank-2.5"; }
        };
        OfficialFaqSearch search = new OfficialFaqSearch(FAQ, java.util.Optional.of(embeddings()),
                java.util.Optional.of(cachedRerank), 0.60);
        for (String set : List.of("dev", "test")) {
            int pos = 0, top1 = 0, shown = 0, negShown = 0, neg = 0;
            for (Case c : readCases().stream().filter(x -> x.set().equals(set)).toList()) {
                var m = search.search(c.question());
                if (c.negative()) {
                    neg++;
                    if (m.isPresent()) negShown++;
                    continue;
                }
                pos++;
                if (m.isPresent()) shown++;
                if (m.isPresent() && c.expected().contains(m.get().item().no())) top1++;
            }
            System.out.printf("[운영 경로 %s] 정답 1위 %d/%d · 표시 %d/%d · 부정 표시 %d/%d%n", set, top1, pos, shown, pos, negShown, neg);
            org.assertj.core.api.Assertions.assertThat(top1).as(set + " 1위").isGreaterThanOrEqualTo(22);
            org.assertj.core.api.Assertions.assertThat(negShown).as(set + " 부정 오탐(test 1건 기록)").isLessThanOrEqualTo(1);
        }
    }

    void report(String set) throws IOException {
        List<OfficialFaq.Item> items = FAQ.items();
        List<String> titles = items.stream().map(OfficialFaq.Item::question).toList();
        List<String> full = items.stream().map(i -> i.question() + "\n" + i.answer()).toList();
        Bm25Index bmTitle = Bm25Index.statsOf(titles);
        Bm25Index bmFull = Bm25Index.statsOf(full);
        EmbeddingClient emb = embeddings();
        List<float[]> docVec = emb.embed(titles, InputType.DOCUMENT);

        List<Case> cases = readCases().stream().filter(c -> c.set().equals(set)).toList();
        Map<String, int[]> hits = new java.util.LinkedHashMap<>();   // 방식 → {top1, top3}
        List<String> rerankRows = new ArrayList<>();
        int positives = (int) cases.stream().filter(c -> !c.negative()).count();

        for (Case c : cases) {
            Bm25Index.Query q = Bm25Index.query(c.question());
            float[] qv = emb.embed(List.of(c.question()), InputType.QUERY).get(0);
            double[] cos = new double[items.size()];
            for (int i = 0; i < items.size(); i++) cos[i] = Cosine.similarity(qv, docVec.get(i));

            List<Integer> rTitle = rank(items.size(), i -> bmTitle.score(q, titles.get(i)));
            List<Integer> rFull = rank(items.size(), i -> bmFull.score(q, full.get(i)));
            List<Integer> rVec = rank(items.size(), i -> cos[i]);
            Map<Integer, Integer> posT = new HashMap<>(), posV = new HashMap<>();
            for (int r = 0; r < rTitle.size(); r++) posT.put(rTitle.get(r), r + 1);
            for (int r = 0; r < rVec.size(); r++) posV.put(rVec.get(r), r + 1);
            List<Integer> rHybrid = rank(items.size(), i -> 0.5 / (60 + posT.get(i)) + 0.5 / (60 + posV.get(i)));

            List<Integer> cand = rHybrid.subList(0, 20);
            double[] rs = NoticeQaSemanticEvalTest.rerankScores(c.question(), cand.stream().map(i -> rerankDoc(items.get(i))).toList());
            Integer[] ord = IntStream.range(0, cand.size()).boxed().toArray(Integer[]::new);
            Arrays.sort(ord, (a, b) -> Double.compare(rs[b], rs[a]));
            List<Integer> rRerank = Arrays.stream(ord).map(cand::get).toList();

            Map<String, List<Integer>> ranks = Map.of("BM25 제목", rTitle, "BM25 제목+답", rFull, "벡터(제목)", rVec,
                    "하이브리드", rHybrid, "하이브리드→rerank", rRerank);
            for (var e : ranks.entrySet()) {
                int[] h = hits.computeIfAbsent(e.getKey(), k -> new int[2]);
                if (c.negative()) continue;
                if (c.expected().contains(items.get(e.getValue().get(0)).no())) h[0]++;
                if (e.getValue().subList(0, 3).stream().anyMatch(i -> c.expected().contains(items.get(i).no()))) h[1]++;
            }
            int top = rRerank.get(0);
            boolean correct = c.expected().contains(items.get(top).no());
            rerankRows.add(String.format("  %.3f %-4s %s → Q%d %s", rs[ord[0]],
                    c.negative() ? "부정" : correct ? "정답" : "오답", c.question(), items.get(top).no(),
                    items.get(top).question().substring(0, Math.min(40, items.get(top).question().length()))));
        }

        System.out.printf("%n=== 공식 FAQ 검색 eval [%s] — 정답 있는 질문 %d, 부정 %d ===%n", set, positives, cases.size() - positives);
        hits.forEach((k, h) -> System.out.printf("  %-16s 1위 %d/%d · 3위 안 %d/%d%n", k, h[0], positives, h[1], positives));
        System.out.println("rerank 1위 점수(높은 순):");
        rerankRows.stream().sorted(Comparator.reverseOrder()).forEach(System.out::println);
    }
}
