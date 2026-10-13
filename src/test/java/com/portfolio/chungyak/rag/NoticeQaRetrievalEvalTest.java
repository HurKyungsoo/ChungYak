package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.domain.DocumentChunk;
import com.portfolio.chungyak.rag.VectorSearch.Hit;
import com.portfolio.chungyak.repository.DocumentChunkRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 공고문 Q&A <b>검색</b> eval (#39) — 실제 공고문 3건 원문으로, 질문의 정답 근거가 LLM 에 넘기는 발췌 안에 들어가는지 잰다.
 *
 * 운영과 같은 경로다: {@link ChunkSplitter}(운영 설정 900/150) → {@link VectorSearch#searchWithin}(BM25 모드 —
 * 운영 서버에 임베딩 키가 없다) → 상위 {@code context-chunks}(2)개. LLM 은 부르지 않는다(키·비용 없이 매 빌드에서 돈다).
 * 답의 정확도는 검색이 정답 발췌를 넘겨야 시작된다 — 여기서 놓치면 어떤 모델도 맞게 답할 수 없다.
 *
 * 차이: 운영 BM25 IDF 는 공고 ~300건 전체 코퍼스 기준이고 여기는 3건 기준이다. 순위가 조금 다를 수 있다.
 * 기준선 아래로 떨어지면(검색·청크·동의어를 바꾼 뒤) 실패한다 — 올렸으면 기준선도 올린다.
 */
class NoticeQaRetrievalEvalTest {

    /** 운영 rag.qa.context-chunks — LLM 에 넘기는 발췌 수(application.yml 과 맞출 것) */
    private static final int CONTEXT_CHUNKS = 3;
    /** 운영 rag.qa.keyword-min-score — 최고 발췌의 키워드 겹침이 이 미만이면 LLM 을 부르지 않고 "관련 내용 없음" */
    private static final double KEYWORD_MIN = 0.5;

    record Case(String notice, String question, String expected) {
        /** 정답 근거 — 공고문에 다른 문장으로 여러 번 있으면 " || " 로 나열, 하나라도 발췌에 있으면 적중 */
        List<String> answers() {
            return List.of(expected.split("\\s*\\|\\|\\s*"));
        }
    }

    private static final Map<String, Long> IDS = new LinkedHashMap<>();
    private static final Map<Long, List<DocumentChunk>> CHUNKS = new LinkedHashMap<>();
    private static final Map<Long, String> TEXTS = new LinkedHashMap<>();
    private static List<Case> cases;
    private static VectorSearch search;

    @BeforeAll
    static void load() throws IOException {
        cases = readCases();
        ChunkSplitter splitter = new ChunkSplitter(900, 150);
        List<String> all = new ArrayList<>();
        for (Case c : cases) {
            if (IDS.containsKey(c.notice())) continue;
            long id = IDS.size() + 1L;
            IDS.put(c.notice(), id);
            String text = readNotice(c.notice());
            TEXTS.put(id, text);
            List<String> parts = splitter.split(text);
            List<DocumentChunk> chunks = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                chunks.add(new DocumentChunk(id, i, parts.get(i), new float[0], "none", "eval", Instant.EPOCH));
            }
            CHUNKS.put(id, chunks);
            all.addAll(parts);
        }
        DocumentChunkRepository repo = mock(DocumentChunkRepository.class);
        when(repo.findAllContents()).thenReturn(all);
        when(repo.findByAnnouncementId(anyLong())).thenAnswer(inv -> CHUNKS.get(inv.<Long>getArgument(0)));
        search = new VectorSearch(repo, Optional.empty(),
                new RagProperties(null, null, new RagProperties.Qa(CONTEXT_CHUNKS, 0.25, KEYWORD_MIN)));
    }

    @Test
    @DisplayName("케이스의 정답 근거는 공고문 원문에 실제로 있다 (eval 자체 검증)")
    void expectedTextExistsInNotice() {
        for (Case c : cases) {
            String text = squash(TEXTS.get(IDS.get(c.notice())));
            for (String alt : c.answers()) {
                assertThat(text).as("%s — %s", c.notice(), c.question()).contains(squash(alt));
            }
        }
    }

    /**
     * 기준선 (2026-10-13, 38문항): 처음(발췌 2개, 동의어·구어 보정 전) 17 → 구어 불용어·동의어 23 → 발췌 3개 28.
     * 청크 크기는 450·600·1200 모두 900 보다 나빴다.
     */
    static final int BASELINE_ANSWERABLE = 28;

    @Test
    @DisplayName("정답 근거가 LLM 에 넘기는 발췌 안에 있다 — 기준선 이상")
    void retrievalHitRate() {
        int hitTop2 = 0, hitTop5 = 0, answerable = 0;
        StringBuilder report = new StringBuilder();
        for (Case c : cases) {
            long id = IDS.get(c.notice());
            List<Hit> hits = search.searchWithin(id, c.question(), 5);
            int rank = rankOf(hits, c);
            boolean gate = !hits.isEmpty() && hits.get(0).keywordScore() >= KEYWORD_MIN;
            if (rank >= 1 && rank <= CONTEXT_CHUNKS) hitTop2++;
            if (rank >= 1) hitTop5++;
            if (rank >= 1 && rank <= CONTEXT_CHUNKS && gate) answerable++;
            report.append(String.format("%s %-5s %-4s %s%n",
                    rank >= 1 && rank <= CONTEXT_CHUNKS ? (gate ? "O" : "G") : "X",
                    rank < 1 ? "-" : "#" + rank, gate ? "" : "gate", c.question()));
        }
        int n = cases.size();
        report.append(String.format("발췌 %d개 안 %d/%d · 상위 5 안 %d/%d · LLM 까지 가는(키워드 게이트 통과) %d/%d%n",
                CONTEXT_CHUNKS, hitTop2, n, hitTop5, n, answerable, n));
        System.out.println("[공고문 Q&A 검색 eval]\n" + report);

        // 기준선 — 검색·청크를 고쳐 올렸으면 여기도 올린다(내려가면 퇴행)
        assertThat(answerable).as(report.toString()).isGreaterThanOrEqualTo(BASELINE_ANSWERABLE);
    }

    /** 정답 근거가 몇 번째 발췌에 있는지(1부터), 없으면 -1 */
    static int rankOf(List<Hit> hits, Case c) {
        for (int i = 0; i < hits.size(); i++) {
            String content = squash(hits.get(i).content());
            if (c.answers().stream().anyMatch(a -> content.contains(squash(a)))) return i + 1;
        }
        return -1;
    }

    /** 공백·줄바꿈 무시 — PDF 추출 줄바꿈과 청크 이어붙이기(공백/개행)가 달라도 같은 글로 본다 */
    static String squash(String s) {
        return s.replaceAll("\\s+", "");
    }

    static List<Case> readCases() throws IOException {
        List<Case> out = new ArrayList<>();
        try (InputStream in = resource("/eval/notice-qa-cases.tsv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.strip().split("\t");
                out.add(new Case(f[0], f[1], f[2]));
            }
        }
        return out;
    }

    static String readNotice(String name) throws IOException {
        try (InputStream in = new GZIPInputStream(resource("/eval/notices/" + name + ".txt.gz"))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static InputStream resource(String path) {
        InputStream in = NoticeQaRetrievalEvalTest.class.getResourceAsStream(path);
        if (in == null) throw new IllegalStateException("eval 리소스 없음: " + path);
        return in;
    }
}
