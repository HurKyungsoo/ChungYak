package com.portfolio.chungyak.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 문서 집합에 대한 BM25 키워드 점수 — 벡터 검색과 섞어(하이브리드) 쓴다.
 *
 * 순수 함수. 형태소 분석기 의존성 없이 한국어를 다루려고 <b>글자 bi-gram</b> 으로 토큰화한다
 * (CJK IR 에서 널리 쓰는 방식). 라틴·숫자 토큰("1600-1004", "84.97")은 통째로 둔다.
 * 코퍼스 통계(IDF·평균 길이)는 전체 청크로 만들고, 점수는 문서 하나씩 즉석으로 낸다 — 메모리는 아래 주석.
 */
final class Bm25Index {

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private static final Pattern TOKEN =
            Pattern.compile("[가-힣]+|[a-z0-9][a-z0-9.\\-]*");

    /**
     * 질의에서 버리는 의문사·어미 어절 (#22). "신청 자격이 <u>어떻게 되나요</u>" 의 밑줄 부분은 공고문에 거의 없어
     * 키워드 겹침을 깎기만 한다 — BM25 모드에서는 이 겹침이 "관련 내용 없음" 게이트라 정답 질문이 걸러졌다.
     */
    private static final Set<String> QUERY_STOPWORDS = Set.of(
            "어떻게", "어떤", "어떤가요", "뭐", "뭐가", "뭔가요", "뭐예요", "무엇", "무엇인가요", "무슨",
            "언제", "어디", "어디서", "어디에", "얼마", "얼마나", "몇",
            "되나요", "돼", "돼요", "되요", "되는", "인가요", "인지", "하나요", "해요", "해야", "하는",
            "있나요", "있어", "있어요", "있는", "알려줘", "알려주세요", "궁금해요", "좀", "수", "건가요");

    /** 이 어간으로 시작하는 어절도 의문사로 본다 — "언제인가요", "얼마예요", "어떻게요" */
    private static final List<String> QUESTION_STEMS = List.of("언제", "얼마", "어떻", "무엇", "어디", "뭐");

    /*
     * 메모리 (#29): 코퍼스 <b>통계</b>(문서 수·평균 길이·단어별 문서 빈도)만 들고 있다. 청크마다의 단어 빈도는
     * 점수를 낼 때 그 청크 하나만 즉석에서 센다. 예전에는 모든 청크의 단어 빈도 맵을 들고 있어 5,800 청크에
     * ~130MB, 공고문 전문(~19,000 청크)이면 ~426MB 였다(서버 여유 메모리 ~580MB). 공고 하나 안의 검색은
     * 청크 수십 개라 즉석으로 세도 빠르다.
     */
    private final int n;
    private final double avgLen;
    private final Map<String, Integer> docFreq;
    /** {@link #build} 로 만든 경우에만 — 작은 문서 집합(테스트 등)을 순서대로 점수 매길 때 */
    private final List<String> docs;

    private Bm25Index(int n, double avgLen, Map<String, Integer> docFreq, List<String> docs) {
        this.n = n;
        this.avgLen = avgLen;
        this.docFreq = docFreq;
        this.docs = docs;
    }

    /** 코퍼스 통계만 — 문서는 들고 있지 않는다. 큰 코퍼스(전체 청크)용. */
    static Bm25Index statsOf(Iterable<String> documents) {
        Map<String, Integer> df = new HashMap<>();
        long totalLen = 0;
        int count = 0;
        for (String d : documents) {
            List<String> tokens = tokenize(d);
            totalLen += tokens.size();
            count++;
            for (String t : new java.util.HashSet<>(tokens)) df.merge(t, 1, Integer::sum);
        }
        return new Bm25Index(count, count == 0 ? 0 : (double) totalLen / count, df, null);
    }

    /** 통계 + 문서 목록 — {@link #scores}·{@link #termCoverage(String, int)} 를 입력 순서대로 쓸 때 */
    static Bm25Index build(List<String> documents) {
        Bm25Index stats = statsOf(documents);
        return new Bm25Index(stats.n, stats.avgLen, stats.docFreq, List.copyOf(documents));
    }

    /** 질의를 한 번만 분석해 두고 여러 청크에 쓴다 — 확장어(동의어) 포함 bi-gram 과 어절 */
    record Query(List<String> terms, List<String> words) {}

    static Query query(String query) {
        List<String> words = queryWords(query);
        List<String> expanded = new ArrayList<>();
        for (String w : words) {
            expanded.add(w);
            expanded.addAll(QuerySynonyms.of(w));   // "돈" → 보증금·임대료… (#28)
        }
        return new Query(tokenize(String.join(" ", expanded)).stream().distinct().toList(), words);
    }

    /** 한 문서의 BM25 점수 — 통계는 코퍼스 전체, 단어 빈도는 이 문서에서 즉석으로. 매칭이 없으면 0. */
    double score(Query q, String doc) {
        if (n == 0 || avgLen == 0) return 0;
        List<String> tokens = tokenize(doc);
        Map<String, Integer> tf = counts(tokens);
        double out = 0;
        for (String term : q.terms()) {
            int df = docFreq.getOrDefault(term, 0);
            int f = tf.getOrDefault(term, 0);
            if (df == 0 || f == 0) continue;
            double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
            out += idf * f * (K1 + 1) / (f + K1 * (1 - B + B * tokens.size() / avgLen));
        }
        return out;
    }

    /**
     * 질의 <b>어절</b> 중 이 문서에 등장한 비율 (0..1) — "키워드가 얼마나 겹치나" 표시·게이트용.
     * 어절 단위로 센다: 한글 어절은 앞 두 글자(어간)가 문서에 있으면 겹친 것으로 본다 — "자격이"는 "자격"으로.
     * bi-gram 단위로 세면 조사가 붙은 "격이" 같은 조각이 분모만 키워 정답 질문의 겹침이 낮게 나온다.
     */
    static double coverage(Query q, String doc) {
        if (q.words().isEmpty()) return 0;
        Map<String, Integer> tf = counts(tokenize(doc));
        long hit = q.words().stream().filter(w -> tf.getOrDefault(stem(w), 0) > 0
                || QuerySynonyms.of(w).stream().anyMatch(syn -> containsWhole(tf, syn))).count();
        return (double) hit / q.words().size();
    }

    /** {@link #build} 의 문서 순서에 맞춘 BM25 점수 배열. 매칭이 없으면 0. */
    double[] scores(String query) {
        Query q = query(query);
        double[] out = new double[docs.size()];
        for (int i = 0; i < out.length; i++) out[i] = score(q, docs.get(i));
        return out;
    }

    /** {@link #build} 의 i 번째 문서와 질의의 겹침 — {@link #coverage} */
    double termCoverage(String query, int docIndex) {
        if (docIndex < 0 || docIndex >= docs.size()) return 0;
        return coverage(query(query), docs.get(docIndex));
    }

    private static Map<String, Integer> counts(List<String> tokens) {
        Map<String, Integer> m = new HashMap<>();
        for (String t : tokens) m.merge(t, 1, Integer::sum);
        return m;
    }

    /**
     * 동의어는 단어 전체(모든 bi-gram)가 문서에 있어야 겹친 것으로 본다 — 어간만 보면 "임대료"의 "임대"처럼
     * 임대 공고 어디에나 있는 조각에 걸려, 무관한 청크도 "관련 있음"으로 통과한다.
     */
    private static boolean containsWhole(Map<String, Integer> doc, String word) {
        List<String> grams = tokenize(word);
        return !grams.isEmpty() && grams.stream().allMatch(g -> doc.getOrDefault(g, 0) > 0);
    }

    /** 질의 어절 — 소문자화, 의문사·어미 어절({@link #QUERY_STOPWORDS}, {@link #QUESTION_STEMS})을 뺀다. 중복 제거. */
    static List<String> queryWords(String query) {
        if (query == null || query.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        Matcher m = TOKEN.matcher(query.toLowerCase());
        while (m.find()) {
            String w = m.group();
            if (QUERY_STOPWORDS.contains(w) || QUESTION_STEMS.stream().anyMatch(w::startsWith)) continue;
            if (!out.contains(w)) out.add(w);
        }
        return out;
    }

    /** 한글 어절은 앞 두 글자(tokenize 가 만드는 첫 bi-gram), 그 밖은 통째로 */
    private static String stem(String word) {
        boolean hangul = word.charAt(0) >= '가' && word.charAt(0) <= '힣';
        return hangul && word.length() > 2 ? word.substring(0, 2) : word;
    }

    static List<String> tokenize(String s) {
        if (s == null || s.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        Matcher m = TOKEN.matcher(s.toLowerCase());
        while (m.find()) {
            String tok = m.group();
            if (tok.charAt(0) >= '가' && tok.charAt(0) <= '힣') {
                if (tok.length() <= 2) out.add(tok);
                for (int i = 0; i + 2 <= tok.length(); i++) out.add(tok.substring(i, i + 2));
            } else {
                out.add(tok);
            }
        }
        return out;
    }
}
