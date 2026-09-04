package com.portfolio.chungyak.rag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        for (String term : tokenize(query).stream().distinct().toList()) {
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

    /** 질의 토큰 중 이 문서에 등장한 비율 (0..1) — "키워드가 얼마나 겹치나" 표시·게이트용. */
    double termCoverage(String query, int docIndex) {
        List<String> q = tokenize(query).stream().distinct().toList();
        if (q.isEmpty() || docIndex < 0 || docIndex >= n) return 0;
        long hit = q.stream().filter(t -> termFreq.get(docIndex).getOrDefault(t, 0) > 0).count();
        return (double) hit / q.size();
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
