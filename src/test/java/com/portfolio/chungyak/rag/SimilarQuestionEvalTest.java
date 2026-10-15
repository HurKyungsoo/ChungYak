package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.rag.NoticeQaRetrievalEvalTest.Case;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비슷한 질문 답 재사용(#45) 기준값 eval — 같은 뜻 쌍 / 다른 뜻 쌍의 질문 임베딩 코사인(Voyage, 운영과 같은
 * {@code input_type=query})을 재고, 결정론 가드({@link SimilarQuestionGuard})를 통과한 쌍만으로 기준값별 적중·오탐을 출력한다.
 *
 * 다른 뜻 쌍은 손으로 쓴 "모양은 비슷하고 뜻이 다른" 쌍 + {@link NoticeQaRetrievalEvalTest} 의 같은 공고 질문끼리 전부
 * (재사용 캐시는 공고 단위라 같은 공고 쌍만 의미가 있다). 질문은 짧아 임베딩 비용은 거의 0이고 {@code build/eval-cache} 에 캐시한다.
 * 키가 없으면 건너뛴다. 운영 기준값({@code rag.qa.similar-min-score})에서 오탐 0 을 강제한다.
 */
@EnabledIf("com.portfolio.chungyak.rag.NoticeQaSemanticEvalTest#keyPresent")
class SimilarQuestionEvalTest {

    /** application.yml 의 rag.qa.similar-min-score 와 같게 둔다 */
    static final double PRODUCTION_MIN = DocumentQaService.DEFAULT_SIMILAR_MIN_SCORE;

    record Pair(boolean same, String a, String b, String origin) {}

    record Scored(Pair pair, boolean guard, double cos) {}

    static List<Pair> readPairs() throws IOException {
        List<Pair> out = new ArrayList<>();
        try (var in = SimilarQuestionEvalTest.class.getResourceAsStream("/eval/notice-qa-similar-pairs.tsv")) {
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.split("\t");
                out.add(new Pair(f[0].equals("same"), f[1], f[2], "hand"));
            }
        }
        return out;
    }

    /** 같은 공고 질문끼리 전부 — same 에 적은 쌍은 뺀다 */
    static List<Pair> sameNoticePairs(List<Pair> hand) throws IOException {
        Set<String> sameKeys = hand.stream().filter(Pair::same)
                .flatMap(p -> java.util.stream.Stream.of(p.a() + "|" + p.b(), p.b() + "|" + p.a()))
                .collect(Collectors.toSet());
        Map<String, List<String>> byNotice = NoticeQaRetrievalEvalTest.readCases().stream()
                .collect(Collectors.groupingBy(Case::notice, java.util.LinkedHashMap::new,
                        Collectors.mapping(Case::question, Collectors.toList())));
        List<Pair> out = new ArrayList<>();
        byNotice.values().forEach(qs -> {
            for (int i = 0; i < qs.size(); i++)
                for (int j = i + 1; j < qs.size(); j++)
                    if (!sameKeys.contains(qs.get(i) + "|" + qs.get(j))) out.add(new Pair(false, qs.get(i), qs.get(j), "notice"));
        });
        return out;
    }

    @Test
    @DisplayName("질문 임베딩 코사인 × 가드 — 기준값별 적중·오탐, 운영 기준값에서 오탐 0")
    void thresholds() throws IOException {
        List<Pair> hand = readPairs();
        List<Pair> pairs = new ArrayList<>(hand);
        pairs.addAll(sameNoticePairs(hand));

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        EmbeddingClient embeddings = new NoticeQaSemanticEvalTest.CachingEmbeddings(new VoyageEmbeddingClient(
                RestClient.builder().requestFactory(factory).build(),
                new VoyageProperties(NoticeQaSemanticEvalTest.apiKey(), "voyage-4-lite", null, 64)));

        Set<String> texts = new LinkedHashSet<>();
        pairs.forEach(p -> { texts.add(p.a()); texts.add(p.b()); });
        List<String> list = List.copyOf(texts);
        List<float[]> vecs = embeddings.embed(list, InputType.QUERY);
        Map<String, float[]> vec = new java.util.HashMap<>();
        for (int i = 0; i < list.size(); i++) vec.put(list.get(i), vecs.get(i));

        List<Scored> scored = pairs.stream().map(p -> new Scored(p,
                SimilarQuestionGuard.compatible(SimilarQuestionGuard.of(p.a()), SimilarQuestionGuard.of(p.b())),
                Cosine.similarity(vec.get(p.a()), vec.get(p.b())))).toList();

        long sameN = scored.stream().filter(s -> s.pair().same()).count();
        long diffN = scored.size() - sameN;
        System.out.printf("%n=== 비슷한 질문 재사용 eval — 같은 뜻 %d쌍, 다른 뜻 %d쌍(손 %d + 같은 공고 %d) ===%n", sameN, diffN,
                hand.stream().filter(p -> !p.same()).count(), diffN - hand.stream().filter(p -> !p.same()).count());
        System.out.printf("가드 통과: 같은 뜻 %d/%d, 다른 뜻 %d/%d%n",
                scored.stream().filter(s -> s.pair().same() && s.guard()).count(), sameN,
                scored.stream().filter(s -> !s.pair().same() && s.guard()).count(), diffN);

        System.out.println("\n기준값 | 재사용(같은 뜻) | 오탐(가드+코사인) | 오탐(코사인만)");
        for (int t = 80; t <= 98; t++) {
            double min = t / 100.0;
            System.out.printf("  %.2f | %2d/%d | %d | %d%n", min,
                    scored.stream().filter(s -> s.pair().same() && s.guard() && s.cos() >= min).count(), sameN,
                    scored.stream().filter(s -> !s.pair().same() && s.guard() && s.cos() >= min).count(),
                    scored.stream().filter(s -> !s.pair().same() && s.cos() >= min).count());
        }

        System.out.println("\n같은 뜻 쌍 (코사인 오름차순):");
        scored.stream().filter(s -> s.pair().same()).sorted(Comparator.comparingDouble(Scored::cos))
                .forEach(s -> System.out.printf("  %.3f %s  %s / %s%n", s.cos(), s.guard() ? "가드O" : "가드X",
                        s.pair().a(), s.pair().b()));
        System.out.println("\n가드를 통과한 다른 뜻 쌍:");
        scored.stream().filter(s -> !s.pair().same() && s.guard())
                .forEach(s -> System.out.printf("  %.3f [%s] %s / %s%n", s.cos(), s.pair().origin(), s.pair().a(), s.pair().b()));
        System.out.println("\n다른 뜻 쌍 중 코사인 상위 15 (가드 결과 함께):");
        scored.stream().filter(s -> !s.pair().same()).sorted(Comparator.comparingDouble(Scored::cos).reversed()).limit(15)
                .forEach(s -> System.out.printf("  %.3f %s [%s] %s / %s%n", s.cos(), s.guard() ? "가드O" : "가드X",
                        s.pair().origin(), s.pair().a(), s.pair().b()));

        assertThat(scored.stream().filter(s -> !s.pair().same() && s.guard() && s.cos() >= PRODUCTION_MIN))
                .as("운영 기준값 %.2f 에서 다른 뜻 쌍을 재사용하면 안 된다", PRODUCTION_MIN).isEmpty();
    }
}
