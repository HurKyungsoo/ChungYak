package com.portfolio.chungyak.rule;

/**
 * 미충족 요건의 <b>수치 차이</b>로 "이렇게 하면 자격이 생긴다"를 만드는 순수 함수 모음.
 *
 * 규칙마다 같은 문장을 복붙하지 않기 위한 것이다(CLAUDE.md 절대 규칙 5).
 * 여기서 만드는 건 근거 문장에 이미 있는 두 수치(현재값·요건값)의 차이뿐 —
 * 새 제도 수치를 만들지 않는다. 되돌릴 수 없는 격차(혼인 7년 초과)나
 * 소득·자산 초과는 여기서 다루지 않는다(안내할 행동이 없다).
 */
public final class ImprovementHints {

    private ImprovementHints() {}

    /** 청약통장 가입 <b>기간</b> 부족 (6/24개월 요건). */
    public static String accountMonths(int currentMonths, int requiredMonths) {
        return "청약통장을 " + (requiredMonths - currentMonths) + "개월 더 유지하면 가입기간 요건("
                + requiredMonths + "개월)을 충족합니다.";
    }

    /** 해당 공급지역 계속 거주 기간 부족 (우선공급 요건). */
    public static String residenceMonths(int currentMonths, int requiredMonths, String scope) {
        return "해당 공급지역에 " + (requiredMonths - currentMonths) + "개월 더 거주하면 "
                + scope + " 우선공급 거주요건(" + requiredMonths + "개월)을 충족합니다."
                + " 그 전에도 기타지역 물량으로는 신청할 수 있습니다.";
    }

    /** 재당첨 제한 기간 미경과. */
    public static String reWinMonths(int monthsSinceWin, int limitMonths) {
        return "마지막 당첨일로부터 " + (limitMonths - monthsSinceWin) + "개월이 더 지나면 재당첨 제한("
                + limitMonths + "개월)이 풀립니다.";
    }

    /** 민영주택 청약통장 예치금 부족. */
    public static String deposit(int currentWon, int requiredWon) {
        return "청약통장 예치금을 " + AssetRequirement.won(requiredWon - currentWon)
                + " 더 채우면 이 지역 예치금 기준(전용 85㎡ 이하 " + AssetRequirement.won(requiredWon)
                + ")을 충족합니다.";
    }

    /** 국민주택 청약통장 납입 횟수 부족. */
    public static String paymentCount(int currentCount, int requiredCount) {
        return "청약통장을 " + (requiredCount - currentCount) + "회 더 납입하면 국민주택 순위 요건("
                + requiredCount + "회)을 충족합니다.";
    }
}
