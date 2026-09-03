package com.portfolio.chungyak.rule;

/**
 * 일반공급 청약가점 계산 입력.
 *
 * 특별공급 판정({@link ApplicantProfile})과 별개다 — 가점은 되고/안 되고가 아니라
 * 점수를 매긴다. 값을 모르면 null 로 두면 그 항목은 "산정 불가"로 표시된다(추측 안 함).
 */
public record GeneralSupplyInput(

        /** 만 나이. 만 30세 미만 미혼이면 무주택기간이 0으로 산정된다. */
        Integer age,

        /** 혼인 여부 */
        boolean married,

        /** 무주택 기간(개월). 만 30세(또는 그 전 혼인 시 혼인신고일)부터 공고일까지 중 무주택인 기간. */
        Integer houselessMonths,

        /** 부양가족 수(본인 제외 — 배우자 + 동거 직계존속 + 미혼 자녀). */
        Integer dependents,

        /** 청약통장(입주자저축) 가입 기간(개월). */
        Integer accountMonths
) {}
