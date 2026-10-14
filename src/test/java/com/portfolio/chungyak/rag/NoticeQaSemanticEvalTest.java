package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.domain.DocumentChunk;
import com.portfolio.chungyak.rag.NoticeQaRetrievalEvalTest.Case;
import com.portfolio.chungyak.rag.VectorSearch.Hit;
import com.portfolio.chungyak.rag.embedding.EmbeddingClient;
import com.portfolio.chungyak.rag.embedding.VoyageEmbeddingClient;
import com.portfolio.chungyak.rag.embedding.VoyageProperties;
import com.portfolio.chungyak.repository.DocumentChunkRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.yaml.snakeyaml.Yaml;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 공고문 Q&A <b>의미(하이브리드) 검색</b> eval — 같은 38문항으로 BM25 단독과 Voyage 임베딩 + BM25(RRF)를 비교한다.
 * Voyage 를 켤지 정하는 근거(2026-10-14). 기준선을 강제하지 않고 표만 출력한다 — 판단은 사람이 한다.
 *
 * <ul>
 *   <li>키: 환경변수 {@code VOYAGE_API_KEY}, 없으면 gitignore 된 {@code application-local-ai.yml} 의
 *       {@code rag.voyage.api-key}. 둘 다 없으면 건너뛴다(CI·평소 빌드). 키는 어디에도 출력하지 않는다.</li>
 *   <li>과금: 임베딩을 {@code build/eval-cache} 에 텍스트 해시로 저장해 <b>첫 실행만</b> 호출한다
 *       (공고문 3건 ≈ 34만 토큰, voyage-4-lite 무료분의 0.17%). 이후 실행은 호출 0.</li>
 *   <li>게이트: 운영 {@link DocumentQaService} 와 같다 — 최고 발췌의 코사인 ≥ min-score 이거나 키워드 겹침 ≥ keyword-min-score.</li>
 * </ul>
 */
@EnabledIf("keyPresent")
class NoticeQaSemanticEvalTest {

    private static final int CONTEXT_CHUNKS = 3;
    private static final double KEYWORD_MIN = 0.5;
    private static final double COS_MIN = 0.25;
    private static final String MODEL = "voyage-4-lite";
    private static final Path CACHE = Path.of("build", "eval-cache", MODEL);

    private static final Map<String, Long> IDS = new LinkedHashMap<>();
    private static final Map<Long, List<DocumentChunk>> CHUNKS = new LinkedHashMap<>();
    private static List<Case> cases;
    private static List<String> allContents;
    private static EmbeddingClient embeddings;
    private static int apiCalls;

    static boolean keyPresent() {
        return apiKey() != null;
    }

    /** 키를 읽기만 한다 — 로그·예외 메시지에 넣지 않는다 */
    static String apiKey() {
        String env = System.getenv("VOYAGE_API_KEY");
        if (usable(env)) return env;
        Path yml = Path.of("src", "main", "resources", "application-local-ai.yml");
        if (!Files.exists(yml)) return null;
        try (InputStream in = Files.newInputStream(yml)) {
            Map<String, Object> root = new Yaml().load(in);
            Object rag = root == null ? null : root.get("rag");
            Object voyage = rag instanceof Map<?, ?> m ? m.get("voyage") : null;
            Object key = voyage instanceof Map<?, ?> m ? m.get("api-key") : null;
            return key instanceof String s && usable(s) ? s : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean usable(String k) {
        return k != null && !k.isBlank() && !"REPLACE_ME".equals(k) && !k.startsWith("${");
    }

    @BeforeAll
    static void load() throws Exception {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        EmbeddingClient voyage = new VoyageEmbeddingClient(RestClient.builder().requestFactory(factory).build(),
                new VoyageProperties(apiKey(), MODEL, null, 64));
        embeddings = new CachingEmbeddings(voyage);

        cases = NoticeQaRetrievalEvalTest.readCases();
        ChunkSplitter splitter = new ChunkSplitter(900, 150);
        allContents = new ArrayList<>();
        for (Case c : cases) {
            if (IDS.containsKey(c.notice())) continue;
            long id = IDS.size() + 1L;
            IDS.put(c.notice(), id);
            List<String> parts = splitter.split(NoticeQaRetrievalEvalTest.readNotice(c.notice()));
            List<float[]> vecs = embeddings.embed(parts, EmbeddingClient.InputType.DOCUMENT);
            List<DocumentChunk> chunks = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                chunks.add(new DocumentChunk(id, i, parts.get(i), vecs.get(i), MODEL, "eval", Instant.EPOCH));
            }
            CHUNKS.put(id, chunks);
            allContents.addAll(parts);
        }
    }

    private static VectorSearch search(Optional<EmbeddingClient> client, double hybridWeight) {
        DocumentChunkRepository repo = mock(DocumentChunkRepository.class);
        when(repo.findAllContents()).thenReturn(allContents);
        when(repo.findByAnnouncementId(anyLong())).thenAnswer(inv -> CHUNKS.get(inv.<Long>getArgument(0)));
        return new VectorSearch(repo, client, new RagProperties(null,
                new RagProperties.Search(5, hybridWeight), new RagProperties.Qa(CONTEXT_CHUNKS, COS_MIN, KEYWORD_MIN)));
    }

    @Test
    @DisplayName("BM25 단독 vs 하이브리드(벡터 가중치 0.4·0.6·0.8·1.0) — 정답 발췌가 LLM 까지 가는 비율")
    void compare() {
        Map<String, VectorSearch> modes = new LinkedHashMap<>();
        modes.put("BM25", search(Optional.empty(), 0.6));
        for (double w : new double[]{0.4, 0.6, 0.8, 1.0}) modes.put("hybrid " + w, search(Optional.of(embeddings), w));

        StringBuilder table = new StringBuilder(String.format("%-36s", "질문"));
        modes.keySet().forEach(m -> table.append(String.format("%-12s", m)));
        table.append('\n');
        Map<String, int[]> totals = new LinkedHashMap<>();   // [발췌 안, 상위5 안, LLM 까지]
        modes.keySet().forEach(m -> totals.put(m, new int[3]));

        for (Case c : cases) {
            table.append(String.format("%-36s", c.question().length() > 34 ? c.question().substring(0, 34) : c.question()));
            for (var e : modes.entrySet()) {
                List<Hit> hits = e.getValue().searchWithin(IDS.get(c.notice()), c.question(), 5);
                int rank = NoticeQaRetrievalEvalTest.rankOf(hits, c);
                boolean semantic = e.getValue().isSemantic();
                boolean gate = !hits.isEmpty() && (hits.get(0).keywordScore() >= KEYWORD_MIN
                        || (semantic && hits.get(0).score() >= COS_MIN));
                boolean inContext = rank >= 1 && rank <= CONTEXT_CHUNKS;
                int[] t = totals.get(e.getKey());
                if (inContext) t[0]++;
                if (rank >= 1) t[1]++;
                if (inContext && gate) t[2]++;
                table.append(String.format("%-12s", (inContext ? (gate ? "O" : "G") : "X") + (rank < 1 ? " -" : " #" + rank)));
            }
            table.append('\n');
        }
        int n = cases.size();
        table.append("\n모드            발췌3 안   상위5 안   LLM 까지(게이트 통과)\n");
        totals.forEach((m, t) -> table.append(String.format("%-14s %3d/%d    %3d/%d    %3d/%d%n", m, t[0], n, t[1], n, t[2], n)));
        table.append("Voyage API 호출(이번 실행): ").append(apiCalls).append("회 — 0 이면 전부 캐시\n");
        System.out.println("[공고문 Q&A 의미 검색 eval]\n" + table);
    }

    /**
     * 주제 설명문 — 공고문의 어느 부분이 그 주제인지 묘사한다(우리가 쓴 문장, 이용자 글 아님).
     * eval 질문에 맞추지 말고 공고문 일반 구성으로 쓸 것(과적합 방지).
     */
    static final Map<QuestionTopic, String> TOPIC_PROTOTYPES = new java.util.EnumMap<>(Map.of(
            QuestionTopic.DOCUMENTS, "제출 서류 목록과 구비서류, 주민등록등본·가족관계증명서 등 증명서, 서류 제출 방법과 기한, 원본·사본 제출 기준",
            QuestionTopic.REGULATION, "전매제한 기간, 재당첨 제한, 거주의무기간, 분양가상한제 적용 여부와 실거주 의무",
            QuestionTopic.MONEY, "분양가격과 공급금액, 계약금·중도금·잔금 납부 일정과 비율, 중도금 대출과 이자, 발코니 확장·옵션 비용, 취득세·인지세, 임대보증금과 월임대료",
            QuestionTopic.SUPPLY, "공급 세대수, 주택형별 공급 물량과 면적, 특별공급·일반공급 세대 배정",
            QuestionTopic.ELIGIBILITY, "신청 자격, 무주택 세대구성원 요건, 청약통장 가입기간과 납입 횟수, 1순위·2순위 조건, 소득·자산 기준, 해당지역 거주 요건, 당첨자 선정 방법, 부적격 당첨과 청약 제한",
            QuestionTopic.SCHEDULE, "청약 접수 일정, 특별공급 접수일, 당첨자 발표일, 계약 체결 기간, 입주 예정 시기",
            QuestionTopic.LOCATION, "공급 위치와 단지 주소, 견본주택(모델하우스) 위치와 주소, 교통 안내"));

    /** 키워드 주제 꼬리표(무료 대조군) — 청크에 주제 단어가 가장 많이 나오는 주제 */
    static QuestionTopic keywordLabel(String chunk) {
        String q = chunk.replaceAll("\\s+", "");
        QuestionTopic best = QuestionTopic.OTHER;
        int bestN = 0;
        for (QuestionTopic t : TOPIC_PROTOTYPES.keySet()) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(t.wordsPattern()).matcher(q);
            int n = 0;
            while (m.find()) n++;
            if (n > bestN) { bestN = n; best = t; }
        }
        return best;
    }

    @Test
    @DisplayName("주제 보정(질문은 보내지 않음) — BM25 후보를 질문 주제와 가까운 청크 순으로 다시 매긴다")
    void topicBoost() {
        List<QuestionTopic> topics = new ArrayList<>(TOPIC_PROTOTYPES.keySet());
        List<float[]> protoVecs = embeddings.embed(topics.stream().map(TOPIC_PROTOTYPES::get).toList(),
                EmbeddingClient.InputType.QUERY);
        Map<QuestionTopic, float[]> proto = new java.util.EnumMap<>(QuestionTopic.class);
        for (int i = 0; i < topics.size(); i++) proto.put(topics.get(i), protoVecs.get(i));

        VectorSearch bm25 = search(Optional.empty(), 0.6);
        Map<String, java.util.function.BiFunction<QuestionTopic, List<Hit>, List<Hit>>> modes = new LinkedHashMap<>();
        modes.put("BM25", (t, hits) -> hits);
        modes.put("kw-label", (t, hits) -> labelFirst(hits, h -> keywordLabel(h.content()) == t));
        for (double w : new double[]{0.1, 0.2, 0.25, 0.3, 0.35, 0.4, 0.5}) {
            modes.put("emb-rrf" + w, (t, hits) -> topicRrf(hits, chunkVecs(hits), proto.get(t), w));
        }
        modes.put("emb-label", (t, hits) -> {
            List<float[]> v = chunkVecs(hits);
            return labelFirst(hits, h -> argmax(v.get(hits.indexOf(h)), proto) == t);
        });

        StringBuilder table = new StringBuilder(String.format("%-34s%-6s", "질문", "주제"));
        modes.keySet().forEach(m -> table.append(String.format("%-11s", m)));
        table.append('\n');
        Map<String, Integer> answerable = new LinkedHashMap<>();
        modes.keySet().forEach(m -> answerable.put(m, 0));
        for (Case c : cases) {
            QuestionTopic topic = QuestionTopic.classify(c.question());
            List<Hit> candidates = bm25.searchWithin(IDS.get(c.notice()), c.question(), 20);
            table.append(String.format("%-34s%-6s", c.question().length() > 32 ? c.question().substring(0, 32) : c.question(),
                    topic.getLabel()));
            for (var e : modes.entrySet()) {
                List<Hit> ranked = topic == QuestionTopic.OTHER || candidates.isEmpty()
                        ? candidates : e.getValue().apply(topic, candidates);
                int rank = NoticeQaRetrievalEvalTest.rankOf(ranked.subList(0, Math.min(5, ranked.size())), c);
                boolean gate = !ranked.isEmpty() && ranked.get(0).keywordScore() >= KEYWORD_MIN;
                boolean ok = rank >= 1 && rank <= CONTEXT_CHUNKS && gate;
                if (ok) answerable.merge(e.getKey(), 1, Integer::sum);
                table.append(String.format("%-11s", (ok ? "O" : rank >= 1 && rank <= CONTEXT_CHUNKS ? "G" : "X")
                        + (rank < 1 ? " -" : " #" + rank)));
            }
            table.append('\n');
        }
        table.append("\nLLM 까지(게이트 통과):");
        answerable.forEach((m, n) -> table.append(String.format("  %s %d/%d", m, n, cases.size())));
        table.append("\nVoyage API 호출(누적): ").append(apiCalls).append('\n');
        System.out.println("[공고문 Q&A 주제 보정 eval]\n" + table);
    }

    private static List<float[]> chunkVecs(List<Hit> hits) {
        return hits.stream().map(h -> CHUNKS.get(h.announcementId()).get(h.chunkIndex()).getEmbedding()).toList();
    }

    private static QuestionTopic argmax(float[] v, Map<QuestionTopic, float[]> proto) {
        QuestionTopic best = null;
        double bestS = -2;
        for (var e : proto.entrySet()) {
            double s = Cosine.similarity(v, e.getValue());
            if (s > bestS) { bestS = s; best = e.getKey(); }
        }
        return best;
    }

    /** 꼬리표가 질문 주제와 같은 청크를 BM25 순서 그대로 앞으로 */
    private static List<Hit> labelFirst(List<Hit> hits, java.util.function.Predicate<Hit> match) {
        List<Hit> out = new ArrayList<>(hits.stream().filter(match).toList());
        hits.stream().filter(match.negate()).forEach(out::add);
        return out;
    }

    /** BM25 순위와 "주제 설명문과의 코사인" 순위를 RRF 로 섞는다 */
    private static List<Hit> topicRrf(List<Hit> hits, List<float[]> vecs, float[] proto, double w) {
        Integer[] byCos = new Integer[hits.size()];
        for (int i = 0; i < byCos.length; i++) byCos[i] = i;
        java.util.Arrays.sort(byCos, (a, b) -> Double.compare(Cosine.similarity(vecs.get(b), proto),
                Cosine.similarity(vecs.get(a), proto)));
        double[] score = new double[hits.size()];
        for (int i = 0; i < hits.size(); i++) score[i] += (1 - w) / (60.0 + i + 1);
        for (int r = 0; r < byCos.length; r++) score[byCos[r]] += w / (60.0 + r + 1);
        Integer[] order = new Integer[hits.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Double.compare(score[b], score[a]));
        return java.util.Arrays.stream(order).map(hits::get).toList();
    }

    /** 텍스트 해시 → 벡터 파일 캐시. 없는 것만 묶어서 Voyage 에 보낸다 */
    static final class CachingEmbeddings implements EmbeddingClient {
        private final EmbeddingClient delegate;

        CachingEmbeddings(EmbeddingClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<float[]> embed(List<String> texts, InputType type) {
            float[][] out = new float[texts.size()][];
            List<Integer> missing = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                out[i] = read(file(texts.get(i), type));
                if (out[i] == null) missing.add(i);
            }
            if (!missing.isEmpty()) {
                apiCalls++;
                List<float[]> fresh = delegate.embed(missing.stream().map(texts::get).toList(), type);
                for (int j = 0; j < missing.size(); j++) {
                    int i = missing.get(j);
                    out[i] = fresh.get(j);
                    write(file(texts.get(i), type), out[i]);
                }
            }
            return List.of(out);
        }

        @Override
        public String model() {
            return delegate.model();
        }

        private static Path file(String text, InputType type) {
            try {
                byte[] h = MessageDigest.getInstance("SHA-256").digest((type + "|" + text).getBytes(StandardCharsets.UTF_8));
                return CACHE.resolve(HexFormat.of().formatHex(h) + ".vec");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private static float[] read(Path p) {
            if (!Files.exists(p)) return null;
            try (DataInputStream in = new DataInputStream(Files.newInputStream(p))) {
                float[] v = new float[in.readInt()];
                for (int i = 0; i < v.length; i++) v[i] = in.readFloat();
                return v;
            } catch (IOException e) {
                return null;
            }
        }

        private static void write(Path p, float[] v) {
            try {
                Files.createDirectories(p.getParent());
                try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(p))) {
                    out.writeInt(v.length);
                    for (float f : v) out.writeFloat(f);
                }
            } catch (IOException e) {
                throw new IllegalStateException("임베딩 캐시 저장 실패: " + p, e);
            }
        }
    }
}
