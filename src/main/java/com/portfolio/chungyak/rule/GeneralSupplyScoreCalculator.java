package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.rule.GeneralSupplyScore.ScoreItem;
import org.springframework.stereotype.Component;

/**
 * 일반공급 청약가점 계산 (주택공급규칙 별표1).
 *
 * 순수 계산이다 — 같은 입력이면 항상 같은 점수. LLM 은 여기 오지 않는다.
 * 경계값(무주택 1년, 통장 6개월/1년, 부양가족 6명)은 테스트로 못 박는다.
 *
 * 무주택기간(최대 32) + 부양가족수(최대 35) + 청약통장 가입기간(최대 17) = 84.
 */
@Component
public class GeneralSupplyScoreCalculator {

    private static final String L_HOUSELESS = "무주택 기간";
    private static final String L_DEPENDENTS = "부양가족 수";
    private static final String L_ACCOUNT = "청약통장 가입 기간";

    public GeneralSupplyScore calculate(GeneralSupplyInput in) {
        return GeneralSupplyScore.of(
                houselessPeriod(in),
                dependents(in),
                accountPeriod(in));
    }

    /** 무주택 기간 — 1년 미만 2점, 1년당 2점, 15년 이상 32점. 만 30세 미만 미혼은 0점. */
    private ScoreItem houselessPeriod(GeneralSupplyInput in) {
        if (!in.married() && in.age() != null && in.age() < 30) {
            return new ScoreItem(L_HOUSELESS, 0, 32,
                    "만 30세 미만 미혼은 무주택 기간이 0으로 산정됩니다.");
        }
        Integer months = in.houselessMonths();
        if (months == null || months < 0) {
            return new ScoreItem(L_HOUSELESS, null, 32, null);
        }
        if (months < 12) {
            return new ScoreItem(L_HOUSELESS, 2, 32, "무주택 " + months + "개월 (1년 미만) → 2점");
        }
        int years = months / 12;
        int score = Math.min(2 + years * 2, 32);
        return new ScoreItem(L_HOUSELESS, score, 32,
                "무주택 " + years + "년 → " + score + "점 (1년 미만 2점 + 1년당 2점, 상한 32)");
    }

    /** 부양가족 수 — 0명 5점, 1명당 5점, 6명 이상 35점. */
    private ScoreItem dependents(GeneralSupplyInput in) {
        Integer count = in.dependents();
        if (count == null || count < 0) {
            return new ScoreItem(L_DEPENDENTS, null, 35, null);
        }
        int capped = Math.min(count, 6);
        int score = 5 + capped * 5;
        return new ScoreItem(L_DEPENDENTS, score, 35,
                "부양가족 " + count + "명 → " + score + "점 (0명 5점 + 1명당 5점, 6명 이상 35점)");
    }

    /** 청약통장 가입 기간 — 6개월 미만 1점, 6개월~1년 2점, 1년부터 매년 1점, 15년 이상 17점. */
    private ScoreItem accountPeriod(GeneralSupplyInput in) {
        Integer months = in.accountMonths();
        if (months == null || months < 0) {
            return new ScoreItem(L_ACCOUNT, null, 17, null);
        }
        if (months < 6) {
            return new ScoreItem(L_ACCOUNT, 1, 17, "가입 " + months + "개월 (6개월 미만) → 1점");
        }
        if (months < 12) {
            return new ScoreItem(L_ACCOUNT, 2, 17, "가입 " + months + "개월 (6개월~1년) → 2점");
        }
        int years = months / 12;
        int score = Math.min(years + 2, 17);   // 1년→3, 2년→4, ... 15년→17
        return new ScoreItem(L_ACCOUNT, score, 17,
                "가입 " + years + "년 → " + score + "점 (1년 3점 + 1년당 1점, 상한 17)");
    }
}
