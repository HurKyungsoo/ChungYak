package com.portfolio.chungyak.web.view;

import java.util.List;

/**
 * "신청 가능한 공고가 0건"일 때 무엇이 막았는지 — 공고마다 엔진이 남긴 미충족·판정 보류를 요건 종류별로 센다.
 *
 * 새로 판정하지 않는다. 종류는 공통 요건(재당첨·소득·자산·청약통장·거주 — {@code CommonRequirements.KIND_ORDER})과
 * 그 밖의 유형 자체 조건, 입력 부족이다. 공고마다 "그 요건 하나만 풀리면 신청 가능해지는 유형"이 있는 요건을 센다
 * (그런 요건이 둘이면 둘 다, 하나도 없으면 "여러 요건이 함께 걸림").
 *
 * @param announcements 집계한 공고 수(특공 판정 대상 · 물량 있는 유형이 있는 접수중·접수예정 공고)
 * @param blockers      많이 걸린 순
 */
public record MatchBlockers(int announcements, List<Blocker> blockers) {

    /** @param label 요건 이름 · @param count 이 요건 하나만 풀리면 되는 공고 수 · @param example 대표 사유 문장(엔진 문장 그대로) */
    public record Blocker(String label, int count, String example) {}

    public static MatchBlockers empty() {
        return new MatchBlockers(0, List.of());
    }
}
