package com.portfolio.chungyak.domain;

/**
 * 공고문 종류 — 원래 공고 / 정정공고 / 취소공고. (#47)
 *
 * LH 는 정정·취소를 코드로 주지 않고 <b>새 PAN_ID 로 제목에 태그를 붙여</b> 다시 올린다
 * (2026-10-14 라이브: "[정정공고]…" 63건, "(정정)"·"(정정공고)", "[취소공고]태백철암1 …").
 * 그래서 원래 공고와 정정본이 목록에 나란히 보였다. 판별은 {@link NoticeLineage#kindOf}.
 */
public enum NoticeKind {
    ORIGINAL,
    CORRECTION,
    CANCELLATION
}
