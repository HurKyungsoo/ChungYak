package com.portfolio.chungyak.rag;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.portfolio.chungyak.llm.LlmRateLimitedException;
import com.portfolio.chungyak.llm.LlmUsageStats;
import com.portfolio.chungyak.rag.VectorSearch.Hit;
import com.portfolio.chungyak.rag.embedding.Reranker;
import com.portfolio.chungyak.repository.DocumentChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 공고문 Q&A — "이 공고 잔여세대 신청 조건이 뭐야?" 같은 질문에 공고문 근거로 답한다.
 *
 * 흐름: 질문 → {@link VectorSearch#searchWithin} 로 관련 발췌 → {@link DocumentAnswerer} 가 답변.
 * 판정이 아니라 정보 검색이다(방향 3). 답변은 항상 발췌 근거와 함께 화면에 표시된다.
 *
 * 응답기(Anthropic 또는 Groq)가 없으면 DISABLED — 화면에서 입력창을 숨긴다.
 * 임베딩(Voyage)이 없어도 동작한다 — 검색이 BM25(키워드)만으로 돈다(#22). 그때 "관련 발췌 없음" 판단은
 * 코사인이 늘 0 이라 키워드 겹침({@code keyword-min-score})만 본다.
 */
@Slf4j
@Service
public class DocumentQaService {

    private final VectorSearch vectorSearch;
    private final DocumentChunkRepository chunkRepository;
    /** 비슷한 질문 답 재사용(#45) 코사인 기준값 기본 — SimilarQuestionEvalTest 로 정했다 */
    static final double DEFAULT_SIMILAR_MIN_SCORE = 0.85;

    private final Optional<DocumentAnswerer> answerer;
    /** 재정렬(Voyage rerank) — 있으면 하이브리드 상위 후보를 다시 매겨 앞쪽만 LLM 에 넘긴다 */
    private final Optional<Reranker> reranker;
    private final int contextChunks;
    private final double minScore;
    private final double keywordMinScore;
    private final double similarMinScore;

    /**
     * 답 캐시 (#32) — 무료 등급 하루 토큰 한도(200K, Q&A 한 번 ≈ 3,800 토큰)를 아끼려고, 같은 공고·같은 질문은
     * 다시 묻지 않는다. 키에 청크 버전(개수·최대 id)을 넣어 재인덱싱하면 자동으로 새로 묻는다.
     * 토큰을 쓴 결과(ANSWERED·UNGROUNDED)만 담는다 — ANSWER_FAILED(429 등)는 다음 요청에서 다시 시도한다.
     */
    private final Cache<String, QaAnswer> answerCache = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterWrite(Duration.ofHours(24))
            .build();

    /**
     * 비슷한 질문 답 재사용 (#45) — 글자가 달라도 같은 뜻이면("계약금 몇 %예요?" / "계약금 비율이 얼마예요?") Groq 를 다시
     * 부르지 않는다. 키는 공고 + 청크 버전, 값은 그 공고에서 답한 질문들의 {임베딩, 서명, 답}. 질문 원문은 담지 않는다(#40).
     * 질문 임베딩은 검색이 이미 만든 것({@link VectorSearch#cachedQueryVector})이라 Voyage 추가 호출이 없다.
     * 무게 = 기억한 질문 수(벡터 1024차원 ≈ 4KB) — 전체 2,000개 ≈ 8MB.
     */
    private final Cache<String, List<Remembered>> similarCache = Caffeine.newBuilder()
            .maximumWeight(2_000)
            .weigher((String k, List<Remembered> v) -> Math.max(1, v.size()))
            .expireAfterWrite(Duration.ofHours(24))
            .build();

    /** 공고 하나에 기억하는 질문 수 상한 — 넘으면 오래된 것부터 버린다 */
    private static final int SIMILAR_PER_NOTICE = 30;

    private record Remembered(float[] vector, SimilarQuestionGuard.Signature signature, QaAnswer answer) {}

    /** 결과 분포 카운터(#25 운영 관찰) — 질문 문장은 담지 않는다 */
    private final Optional<LlmUsageStats> stats;

    public DocumentQaService(VectorSearch vectorSearch,
                             DocumentChunkRepository chunkRepository,
                             Optional<DocumentAnswerer> answerer,
                             RagProperties properties) {
        this(vectorSearch, chunkRepository, answerer, properties, Optional.empty(), Optional.empty());
    }

    @Autowired
    public DocumentQaService(VectorSearch vectorSearch,
                             DocumentChunkRepository chunkRepository,
                             Optional<DocumentAnswerer> answerer,
                             RagProperties properties,
                             Optional<LlmUsageStats> stats,
                             Optional<Reranker> reranker) {
        this.stats = stats;
        this.reranker = reranker;
        this.vectorSearch = vectorSearch;
        this.chunkRepository = chunkRepository;
        this.answerer = answerer;
        this.contextChunks = properties.qa().contextChunks();
        this.minScore = properties.qa().minScore();
        this.keywordMinScore = properties.qa().keywordMinScore();
        this.similarMinScore = properties.qa().similarMinScore();
    }

    /** 공백·물음표·대소문자 차이는 같은 질문으로 본다. 청크 버전을 못 읽으면 캐시하지 않는다(null). */
    private String cacheKey(Long announcementId, String question) {
        String notice = noticeKey(announcementId);
        if (notice == null) return null;
        String q = question.strip().toLowerCase().replaceAll("[\\s?？!.]+", " ").strip();
        return notice + "|" + q;
    }

    /** 공고 + 청크 버전 — 재인덱싱하면 바뀌어 예전 답을 버린다. 버전을 못 읽으면 null */
    private String noticeKey(Long announcementId) {
        DocumentChunkRepository.IndexVersion v = chunkRepository.indexVersion();
        return v == null ? null : announcementId + "|" + v.getCount() + "|" + v.getMaxId();
    }

    public boolean isEnabled() {
        return answerer.isPresent();
    }

    /** 의미(벡터) 검색까지 하는지 — false 면 BM25(키워드)만 */
    public boolean isSemantic() {
        return vectorSearch.isSemantic();
    }

    /** 이 공고에 인덱싱된 공고문 청크가 있는지 — 화면에서 입력창 노출 여부. */
    public boolean hasIndex(Long announcementId) {
        return chunkRepository.existsByAnnouncementId(announcementId);
    }

    /**
     * NO_MATCH = 검색 자체가 관련 발췌를 못 찾음(질문이 공고문과 무관).
     * ANSWER_FAILED = 관련 발췌는 찾았지만 응답기(LLM) 호출이 실패함(크레딧·네트워크 등).
     * 화면에서 둘을 같은 문구로 뭉개면 "발췌를 찾긴 했는데 왜 답이 없지?"가 "관련 내용이 없다"로 오해된다.
     */
    /**
     * UNGROUNDED = 답변은 만들었지만 금액·날짜가 발췌에 "그 항목의 값"으로 적혀 있지 않아({@link AnswerGrounding})
     * 보여주지 않음 (#31). 표 열이 섞여 다른 값을 단정한 답을 화면에 내보내지 않는다.
     */
    /** FACT = 구조화 데이터(API·공고문 요약표) 값으로 답함 — LLM 을 거치지 않았다(#39, {@link FactAnswerer}) */
    public enum Status { DISABLED, NO_INDEX, NO_MATCH, ANSWER_FAILED, UNGROUNDED, ANSWERED, FACT }

    public record Citation(int chunkIndex, String excerpt, double score, double keywordScore) {}

    /** 금액 표기 — "33,148,000", "6,600,000원", "292만원" */
    private static final java.util.regex.Pattern AMOUNT =
            java.util.regex.Pattern.compile("\\d{1,3}(?:,\\d{3})+|\\d+\\s*(?:만\\s*)?원");

    /**
     * @param source        FACT 일 때 값의 출처(그 밖에는 null)
     * @param weakSentences 발췌에서 근거를 찾지 못한 답 문장(#41, {@link SentenceGrounding}) — 화면이 "원문 확인"으로 표시
     * @param similar       뜻이 비슷한 이전 질문의 답을 다시 썼다(#45) — 화면이 "비슷한 질문의 답"으로 표시
     */
    public record QaAnswer(Status status, String answer, List<Citation> citations, String source, List<String> weakSentences,
                           boolean similar) {
        public QaAnswer {
            weakSentences = weakSentences == null ? List.of() : List.copyOf(weakSentences);
        }

        public QaAnswer(Status status, String answer, List<Citation> citations, String source, List<String> weakSentences) {
            this(status, answer, citations, source, weakSentences, false);
        }

        public QaAnswer(Status status, String answer, List<Citation> citations) {
            this(status, answer, citations, null, List.of());
        }

        public QaAnswer(Status status, String answer, List<Citation> citations, String source) {
            this(status, answer, citations, source, List.of());
        }

        /** 근거가 약한 문장이 있는 AI 답인지 */
        public boolean hasWeakSentences() { return status == Status.ANSWERED && !weakSentences.isEmpty(); }

        public boolean isShown() { return status != Status.DISABLED; }
        /** 비슷한 질문의 답을 다시 쓴 AI 답인지 */
        public boolean isSimilarReuse() { return similar && status == Status.ANSWERED; }

        QaAnswer asSimilar() { return new QaAnswer(status, answer, citations, source, weakSentences, true); }
        public boolean hasAnswer() { return status == Status.ANSWERED || status == Status.FACT; }
        public boolean isFact() { return status == Status.FACT; }

        /**
         * 답변에 금액이 있는지 — 화면이 "주택형마다 다를 수 있으니 원문 확인" 안내를 붙인다.
         * 표 오독 금액은 {@link AnswerGrounding} 이 이미 걸러 UNGROUNDED 로 숨기므로(#31), 여기까지 온 금액은
         * 발췌에서 그 항목 바로 옆에 적힌 값이다.
         */
        public boolean mentionsAmount() { return status == Status.ANSWERED && answer != null && AMOUNT.matcher(answer).find(); }

        static QaAnswer disabled()  { return new QaAnswer(Status.DISABLED, null, List.of()); }
        static QaAnswer noIndex()   { return new QaAnswer(Status.NO_INDEX, null, List.of()); }
        static QaAnswer noMatch(List<Citation> c) { return new QaAnswer(Status.NO_MATCH, null, c); }
        static QaAnswer answerFailed(List<Citation> c) { return new QaAnswer(Status.ANSWER_FAILED, null, c); }
        static QaAnswer answered(String a, List<Citation> c) { return new QaAnswer(Status.ANSWERED, a, c); }
        static QaAnswer answered(String a, List<Citation> c, List<String> weak) { return new QaAnswer(Status.ANSWERED, a, c, null, weak); }
        static QaAnswer fact(String a, String source) { return new QaAnswer(Status.FACT, a, List.of(), source); }
        static QaAnswer ungrounded(List<Citation> c) { return new QaAnswer(Status.UNGROUNDED, null, c); }
    }

    public QaAnswer answer(Long announcementId, String question) {
        if (!isEnabled() || question == null || question.isBlank()) {
            return QaAnswer.disabled();
        }

        String key = cacheKey(announcementId, question);
        QaAnswer cached = key == null ? null : answerCache.getIfPresent(key);
        if (cached != null) {
            log.debug("공고 #{} Q&A 캐시 적중", announcementId);
            count("CACHE_HIT");
            return cached;
        }
        QaAnswer fresh = answerUncached(announcementId, question);
        count(fresh.status().name());
        if (key != null && (fresh.status() == Status.ANSWERED || fresh.status() == Status.UNGROUNDED)) {
            answerCache.put(key, fresh);
        }
        return fresh;
    }

    private QaAnswer answerUncached(Long announcementId, String question) {
        int fetch = reranker.map(r -> Math.max(contextChunks, r.candidates())).orElse(contextChunks);
        List<Hit> hits = vectorSearch.searchWithin(announcementId, question.strip(), fetch);
        if (hits.isEmpty()) {
            // BM25 모드는 키워드가 하나도 안 걸린 청크를 빼므로, 인덱스가 있어도 결과가 빌 수 있다 — 그건 "관련 없음"이다
            return hasIndex(announcementId) ? QaAnswer.noMatch(List.of()) : QaAnswer.noIndex();
        }

        // 비슷한 질문 답 재사용(#45) — 검색이 만든 질문 임베딩으로 본다. 재정렬·LLM 둘 다 아낀다
        String notice = noticeKey(announcementId);
        Optional<float[]> vector = vectorSearch.cachedQueryVector(question);
        SimilarQuestionGuard.Signature signature = SimilarQuestionGuard.of(question);
        Optional<QaAnswer> similar = vector.flatMap(v -> findSimilar(notice, v, signature));
        if (similar.isPresent()) {
            log.info("공고 #{} Q&A — 비슷한 질문의 답을 다시 씀", announcementId);
            count("SIMILAR_HIT");
            return similar.get();
        }

        QaAnswer fresh = answerFromHits(announcementId, question, rerank(question.strip(), hits));
        if (vector.isPresent() && (fresh.status() == Status.ANSWERED || fresh.status() == Status.UNGROUNDED)) {
            remember(notice, new Remembered(vector.get(), signature, fresh));
        }
        return fresh;
    }

    /** 서명이 같고 코사인이 기준 이상인 이전 질문 중 가장 가까운 것의 답 */
    private Optional<QaAnswer> findSimilar(String notice, float[] vector, SimilarQuestionGuard.Signature signature) {
        if (notice == null || similarMinScore > 1) return Optional.empty();
        List<Remembered> seen = similarCache.getIfPresent(notice);
        if (seen == null) return Optional.empty();
        Remembered best = null;
        double bestScore = similarMinScore;
        synchronized (seen) {
            for (Remembered r : seen) {
                if (!SimilarQuestionGuard.compatible(r.signature(), signature)) continue;
                double cos = Cosine.similarity(r.vector(), vector);
                if (cos >= bestScore) {
                    best = r;
                    bestScore = cos;
                }
            }
        }
        return Optional.ofNullable(best).map(r -> r.answer().asSimilar());
    }

    private void remember(String notice, Remembered entry) {
        if (notice == null || similarMinScore > 1) return;
        similarCache.asMap().compute(notice, (k, old) -> {
            List<Remembered> list = old == null ? new ArrayList<>() : old;
            synchronized (list) {
                list.add(entry);
                if (list.size() > SIMILAR_PER_NOTICE) list.remove(0);
            }
            return list;
        });
    }

    private QaAnswer answerFromHits(Long announcementId, String question, List<Hit> hits) {
        hits = hits.subList(0, Math.min(contextChunks, hits.size()));

        List<Citation> citations = hits.stream()
                .map(h -> new Citation(h.chunkIndex(), h.content(), h.score(), h.keywordScore()))
                .toList();

        Hit top = hits.get(0);
        boolean weakMeaning = !vectorSearch.isSemantic() || top.score() < minScore;
        if (weakMeaning && top.keywordScore() < keywordMinScore) {
            log.info("공고 #{} Q&A — 최고 발췌 코사인 {} · 키워드 {} 로 관련 발췌 없음", announcementId,
                    String.format("%.2f", top.score()), String.format("%.2f", top.keywordScore()));
            return QaAnswer.noMatch(citations);
        }

        List<String> numbered = new java.util.ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            numbered.add("[" + (i + 1) + "] " + hits.get(i).content());
        }

        try {
            // 일상어 용어 힌트(#39) — "모델하우스" 질문에 "견본주택" 발췌를 보고도 못 잇는 일이 있었다
            String text = answerer.get().answer(question.strip() + QuerySynonyms.hintLine(question), numbered);
            if (text.isBlank()) return QaAnswer.answerFailed(citations);
            // 금액·날짜 근거 검사(#31) — 어긋나면 답을 숨기고 발췌만 보여준다. 다시 만들지 않는다: 같은 발췌면
            // 같은 표를 같은 식으로 잘못 읽어(실사용 2/2 재실패) 무료 등급 분당 한도(429)만 깎았다.
            Optional<String> problem = AnswerGrounding.check(text, numbered);
            if (problem.isEmpty()) {
                // 서술 문장 근거(#41) — 답은 보여 주되 발췌에서 확인되지 않는 문장을 표시한다
                List<String> weak = SentenceGrounding.weakSentences(text, numbered);
                if (!weak.isEmpty()) {
                    count("WEAK_SENTENCES");
                    log.info("공고 #{} Q&A — 발췌에서 근거를 못 찾은 문장 {}개", announcementId, weak.size());
                }
                return QaAnswer.answered(text, citations, weak);
            }
            log.warn("공고 #{} Q&A 답변 근거 불일치 — 답을 숨김: {}", announcementId, problem.get());
            return QaAnswer.ungrounded(citations);
        } catch (RuntimeException e) {
            if (LlmRateLimitedException.isRateLimit(e)) count("RATE_LIMITED");
            log.warn("공고 #{} Q&A 응답 생성 실패 — {}", announcementId, e.toString());
            return QaAnswer.answerFailed(citations);
        }
    }

    /** 재정렬 — 실패하면 하이브리드 순서 그대로(답을 막지 않는다) */
    private List<Hit> rerank(String question, List<Hit> hits) {
        if (reranker.isEmpty() || hits.size() <= 1) return hits;
        try {
            List<Integer> order = reranker.get().order(question, hits.stream().map(Hit::content).toList());
            if (order.size() != hits.size()) throw new IllegalStateException("rerank 결과 개수 불일치");
            count("RERANKED");
            return order.stream().map(hits::get).toList();
        } catch (RuntimeException e) {
            count("RERANK_FAILED");
            log.warn("Q&A 재정렬 실패 — 하이브리드 순서로 답함: {}", e.getClass().getSimpleName());
            return hits;
        }
    }

    void count(String outcome) {
        stats.ifPresent(st -> st.count("qa." + outcome));
    }
}
