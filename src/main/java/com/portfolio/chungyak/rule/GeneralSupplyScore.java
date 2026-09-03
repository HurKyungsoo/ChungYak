package com.portfolio.chungyak.rule;

import java.util.ArrayList;
import java.util.List;

/**
 * 일반공급 청약가점 결과 (84점 만점).
 *
 * 무주택기간(32) + 부양가족수(35) + 청약통장 가입기간(17).
 * 각 항목에 점수와 <b>계산 근거</b>를 함께 담는다 — 이 프로젝트는 이유 없는 판정을 안 낸다.
 * 입력이 부족한 항목은 점수 null + {@code missingInputs} 에 이름이 들어간다.
 */
public record GeneralSupplyScore(
        Integer total,                 // 항목 하나라도 산정 불가면 null
        ScoreItem houselessPeriod,     // 무주택 기간
        ScoreItem dependents,          // 부양가족 수
        ScoreItem accountPeriod,       // 청약통장 가입 기간
        List<String> missingInputs) {

    public static final int MAX_TOTAL = 84;

    /** 가점 항목 하나: 라벨 / 점수(산정 불가 시 null) / 만점 / 계산 근거 문장 */
    public record ScoreItem(String label, Integer score, int max, String detail) {

        boolean scored() {
            return score != null;
        }
    }

    static GeneralSupplyScore of(ScoreItem houseless, ScoreItem dependents, ScoreItem account) {
        List<String> missing = new ArrayList<>();
        if (!houseless.scored()) missing.add(houseless.label());
        if (!dependents.scored()) missing.add(dependents.label());
        if (!account.scored()) missing.add(account.label());

        Integer total = missing.isEmpty()
                ? houseless.score() + dependents.score() + account.score()
                : null;

        return new GeneralSupplyScore(total, houseless, dependents, account, missing);
    }

    public boolean isComplete() {
        return total != null;
    }
}
