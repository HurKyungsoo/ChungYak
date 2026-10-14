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
 * 공고문 Q&A <b>검색</b> eval (#39) — 실제 공고문 7건 원문으로, 질문의 정답 근거가 LLM 에 넘기는 발췌 안에 들어가는지 잰다.
 *
 * 경로: {@link ChunkSplitter}(운영 설정 900/150) → {@link VectorSearch#searchWithin}(BM25 모드) → 상위
 * {@code context-chunks}(3)개. LLM 은 부르지 않는다(키·비용 없이 매 빌드에서 돈다).
 * 운영은 2026-10-14 부터 Voyage 하이브리드 + rerank 다 — 그 경로는 {@code NoticeQaSemanticEvalTest}(키 있을 때만)가 잰다.
 * 이 BM25 숫자는 키 없는 로컬·키가 빠진 운영의 하한이자, 매 빌드에서 도는 퇴행 감시용이다.
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
        // " || " 대안은 같은 내용의 다른 모양(예: 표를 편 추출 #42 와 위치순 추출)이라 하나만 있으면 된다
        for (Case c : cases) {
            String text = squash(TEXTS.get(IDS.get(c.notice())));
            assertThat(c.answers()).as("%s — %s", c.notice(), c.question())
                    .anyMatch(alt -> text.contains(squash(alt)));
        }
    }

    /**
     * 기준선 (2026-10-13, 38문항): 처음(발췌 2개, 동의어·구어 보정 전) 17 → 구어 불용어·동의어 23 → 발췌 3개 28.
     * 청크 크기는 450·600·1200 모두 900 보다 나빴다.
     * 2026-10-14 공고문 4건·31문항 추가(공고문을 읽기 전에 쓴 hold-out) → 69문항 중 43 (기존 38 중 29, 새 31 중 14).
     * 기존 문항 수치가 28→29 로 바뀐 건 공고문이 늘어 BM25 IDF 가 달라졌기 때문이다.
     * 2026-10-14 (#42) 공고문 6건을 표를 행 단위로 편 추출로 바꿈 → 43 에서 34. 표 글이 늘어 청크가 바뀌어 BM25 만으로는
     * 손해다(" | " 만 쓰면 40, 페이지 끝에 덧붙이면 35~38). 운영 경로(하이브리드 + rerank 상위 20)는 같은 텍스트로
     * 61 → 62 라 바꿨다 — NoticeQaSemanticEvalTest 참고. 2026000465 는 PDF 를 다시 못 받아 예전 텍스트 그대로다.
     */
    static final int BASELINE_ANSWERABLE = 34;

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

    record TableCase(String notice, String question, String value, String label) {}

    /**
     * 표 값 (#42) — 값이 LLM 에 넘기는 발췌에 있고(검색), 그 발췌의 한 줄에 값과 열·행 이름이 같이 있는지(읽힘).
     * 위치순 추출은 값이 발췌에 있어도 이름과 다른 줄에 흩어져 LLM 이 다른 칸 값을 답했다.
     * 기준선 (2026-10-14, 9문항, BM25): 위치순 추출 검색 5 · 읽힘 5 → 표를 행 단위로 편 추출 6 · 6.
     * "읽힘"은 같은 줄 대리 지표라 위치순도 머리글이 값과 한 줄에 섞여 걸리는 경우가 있다 — 실제 차이는 행 모양이다
     * (위치순 "059.9800A 33,148,000 6,600,000 26,548,000 292,150 …" → "… 임대조건 월 임대료: 292,150").
     */
    static final int TABLE_BASELINE_RETRIEVED = 6;
    static final int TABLE_BASELINE_READABLE = 6;

    @Test
    @DisplayName("표 값이 발췌에 있고 같은 줄에 이름과 함께 읽힌다 — 기준선 이상")
    void tableValuesReadable() throws IOException {
        List<TableCase> tableCases = new ArrayList<>();
        try (InputStream in = resource("/eval/notice-qa-table-cases.tsv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.strip().split("\t");
                tableCases.add(new TableCase(f[0], f[1], f[2], f[3]));
            }
        }
        int retrieved = 0, readable = 0;
        StringBuilder report = new StringBuilder();
        for (TableCase c : tableCases) {
            assertThat(TEXTS.get(IDS.get(c.notice()))).as("eval 자체 검증 — %s", c.question()).contains(c.value());
            List<Hit> hits = search.searchWithin(IDS.get(c.notice()), c.question(), CONTEXT_CHUNKS);
            boolean inExcerpt = hits.stream().anyMatch(h -> h.content().contains(c.value()));
            boolean sameLine = hits.stream().flatMap(h -> h.content().lines())
                    .anyMatch(l -> l.contains(c.value()) && l.contains(c.label()));
            if (inExcerpt) retrieved++;
            if (sameLine) readable++;
            report.append(String.format("%s%s %s%n", inExcerpt ? "검색" : "----", sameLine ? "·읽힘" : "     ", c.question()));
        }
        report.append(String.format("표 값 %d개 — 발췌 안 %d · 같은 줄에 이름과 %d%n", tableCases.size(), retrieved, readable));
        System.out.println("[공고문 표 값 eval]\n" + report);
        assertThat(retrieved).as(report.toString()).isGreaterThanOrEqualTo(TABLE_BASELINE_RETRIEVED);
        assertThat(readable).as(report.toString()).isGreaterThanOrEqualTo(TABLE_BASELINE_READABLE);
    }

    /** 표 값 결정론 답변(#42)이 답한 수 — 늘리면 올린다. 답한 것은 모두 정답 값을 담아야 한다(아래 검사). */
    static final int TABLE_FACT_BASELINE_ANSWERED = 6;   // 2026-10-14: 9문항 중 6(자산 기준·최대전환 임대료만 있는 3000 은 넘긴다)

    @Test
    @DisplayName("표 값 결정론 답변 — 답하면 반드시 맞고, 일반 질문에는 끼어들지 않는다")
    void tableFactAnswers() throws IOException {
        StringBuilder report = new StringBuilder();
        int answered = 0;
        try (InputStream in = resource("/eval/notice-qa-table-cases.tsv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.strip().split("\t");
                var fact = TableFactAnswerer.answer(TEXTS.get(IDS.get(f[0])), f[1]);
                if (fact.isPresent()) {
                    answered++;
                    assertThat(fact.get().text()).as("결정론 답은 표 값을 담아야 한다 — %s", f[1]).contains(f[2]);
                }
                report.append(fact.isPresent() ? "답함 " : "넘김 ").append(f[1]).append('\n');
            }
        }
        // 일반 질문(방법·자격·서류 …)에 표 행으로 답하면 안 된다 — 답했다면 정답 근거가 그 안에 있어야 한다
        for (Case c : cases) {
            var fact = TableFactAnswerer.answer(TEXTS.get(IDS.get(c.notice())), c.question());
            if (fact.isEmpty()) continue;
            String text = squash(fact.get().text());
            report.append("일반 질문에 답함: ").append(c.question()).append('\n');
            assertThat(c.answers()).as("일반 질문에 끼어든 결정론 답 — %s", c.question())
                    .anyMatch(a -> text.contains(squash(a)));
        }
        System.out.println("[표 값 결정론 답변]\n" + report + "답함 " + answered);
        assertThat(answered).as(report.toString()).isGreaterThanOrEqualTo(TABLE_FACT_BASELINE_ANSWERED);
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
