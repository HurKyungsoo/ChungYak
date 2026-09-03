package com.portfolio.chungyak.rule;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 재당첨 제한 기간 (주택공급규칙 제54조). 개월.
 *
 * 제도 개정으로 자주 바뀐다. `application.yml` 로 둔다.
 *  - 투기과열지구·청약과열지역 당첨: 10년
 *  - 그 외: 보수적으로 5년(수도권 과밀억제권역) 적용
 */
@ConfigurationProperties(prefix = "re-win-restriction")
public record ReWinRestrictionProperties(
        int speculationAreaMonths,
        int defaultMonths) {

    public ReWinRestrictionProperties {
        if (speculationAreaMonths <= 0) speculationAreaMonths = 120;
        if (defaultMonths <= 0) defaultMonths = 60;
    }

    public int monthsFor(boolean pastWinInSpeculationArea) {
        return pastWinInSpeculationArea ? speculationAreaMonths : defaultMonths;
    }
}
