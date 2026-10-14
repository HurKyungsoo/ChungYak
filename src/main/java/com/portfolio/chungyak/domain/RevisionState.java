package com.portfolio.chungyak.domain;

/**
 * 같은 공고 계보(원래 공고 + 정정본 + 취소공고) 안에서 이 공고의 위치. (#47)
 * 목록·캘린더·매칭·알림은 {@link #CURRENT} 만 보여 준다. 상세 화면은 셋 다 열리고 상태를 밝힌다.
 */
public enum RevisionState {
    /** 계보의 최신본 — 정정이 없었으면 원래 공고 자신 */
    CURRENT,
    /** 정정공고가 나와 낡은 본 */
    SUPERSEDED,
    /** 취소공고 자신, 또는 취소공고가 가리키는 공고 */
    CANCELLED
}
