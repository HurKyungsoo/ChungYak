package com.portfolio.chungyak.rule;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 민영주택 일반공급의 가점제 적용비율(전용면적 구간별) — 주택공급에 관한 규칙 제28조.
 *
 * 투기과열지구·청약과열지역(이 프로젝트의 {@code RegulationFlags.isRegulatedArea()})에만
 * 적용되는 전국 고정 비율이다. 그 외 지역은 시장·군수·구청장이 40% 이하 범위에서
 * 정해 공고하므로 전국 공통 수치가 없다 — 여기 두지 않는다.
 *
 * 제도·고시로 바뀌므로 코드에 박지 않고 `application.yml`(general-supply-lottery) 에 둔다.
 */
@ConfigurationProperties(prefix = "general-supply-lottery")
public record GeneralSupplyLotteryProperties(RegulatedPointRatio regulatedPointRatio) {

    public GeneralSupplyLotteryProperties {
        if (regulatedPointRatio == null) {
            regulatedPointRatio = new RegulatedPointRatio(40, 70, 80);
        }
    }

    /** 전용면적 구간별 가점제 비율(%). 나머지는 추첨제. */
    public record RegulatedPointRatio(int upTo60Sqm, int upTo85Sqm, int over85Sqm) {}

    public int pointRatioPercent(double exclusiveAreaSqm) {
        if (exclusiveAreaSqm <= 60) return regulatedPointRatio.upTo60Sqm();
        if (exclusiveAreaSqm <= 85) return regulatedPointRatio.upTo85Sqm();
        return regulatedPointRatio.over85Sqm();
    }
}
