package com.portfolio.chungyak.rag;

import java.util.ArrayList;
import java.util.Arrays;
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
 * 코퍼스가 수천 건을 넘어가면 역색인/캐시가 필요하지만, 지금 규모(공고 수백)에선
 * 질의마다 새로 만들어도 충분하다.
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

    private final int n;
    private final List<Map<String, Integer>> termFreq;
    private final int[] docLen;
    private final double avgLen;
    private final Map<String, Integer> docFreq;

    private Bm25Index(int n, List<Map<String, Integer>> termFreq, int[] docLen,
                      double avgLen, Map<String, Integer> docFreq) {
        this.n = n;
        this.termFreq = termFreq;
        this.docLen = docLen;
        this.avgLen = avgLen;
        this.docFreq = docFreq;
    }

    static Bm25Index build(List<String> documents) {
        List<Map<String, Integer>> tf = new ArrayList<>(documents.size());
        int[] len = new int[documents.size()];
        Map<String, Integer> df = new HashMap<>();

        for (int i = 0; i < documents.size(); i++) {
            List<String> tokens = tokenize(documents.get(i));
            Map<String, Integer> counts = new HashMap<>();
            for (String t : tokens) counts.merge(t, 1, Integer::sum);
            tf.add(counts);
            len[i] = tokens.size();
            for (String t : counts.keySet()) df.merge(t, 1, Integer::sum);
        }
        double avg = documents.isEmpty() ? 0 : Arrays.stream(len).average().orElse(0);
        return new Bm25Index(documents.size(), tf, len, avg, df);
    }

    /** 입력 문서 순서에 맞춘 BM25 점수 배열. 매칭이 없으면 0. */
    double[] scores(String query) {
        double[] out = new double[n];
        if (n == 0 || avgLen == 0) return out;

        for (String term : tokenize(String.join(" ", queryWords(query))).stream().distinct().toList()) {
            int df = docFreq.getOrDefault(term, 0);
            if (df == 0) continue;
            double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
            for (int i = 0; i < n; i++) {
                int f = termFreq.get(i).getOrDefault(term, 0);
                if (f == 0) continue;
                double tfNorm = f * (K1 + 1) / (f + K1 * (1 - B + B * docLen[i] / avgLen));
                out[i] += idf * tfNorm;
            }
        }
        return out;
    }

    /**
     * 질의 <b>어절</b> 중 이 문서에 등장한 비율 (0..1) — "키워드가 얼마나 겹치나" 표시·게이트용.
     * 어절 단위로 센다: 한글 어절은 앞 두 글자(어간)가 문서에 있으면 겹친 것으로 본다 — "자격이"는 "자격"으로.
     * bi-gram 단위로 세면 조사가 붙은 "격이" 같은 조각이 분모만 키워 정답 질문의 겹침이 낮게 나온다.
     */
    double termCoverage(String query, int docIndex) {
        List<String> words = queryWords(query);
        if (words.isEmpty() || docIndex < 0 || docIndex >= n) return 0;
        long hit = words.stream().filter(w -> termFreq.get(docIndex).getOrDefault(stem(w), 0) > 0).count();
        return (double) hit / words.size();
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
