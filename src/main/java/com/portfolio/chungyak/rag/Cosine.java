package com.portfolio.chungyak.rag;

/** 코사인 유사도 — 앱 메모리 벡터 검색용. 순수 함수. */
final class Cosine {

    private Cosine() {}

    /** @return -1..1. 한쪽이라도 영벡터/길이 불일치면 0. */
    static double similarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) return 0.0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
