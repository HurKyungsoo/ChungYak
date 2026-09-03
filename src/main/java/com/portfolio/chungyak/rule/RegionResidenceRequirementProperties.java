package com.portfolio.chungyak.rule;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 해당 공급지역 거주요건 (계속 거주 기간, 개월).
 *
 * - 투기과열지구·청약과열지역: 2년(24개월) 이상 계속 거주해야 우선/1순위
 * - 수도권(서울·경기·인천) 그 외: 1년(12개월)
 * - 비수도권: 지자체 조례 — 이 서비스는 판정하지 않음(통과)
 *
 * 지역·고시로 바뀌므로 `application.yml`(region-residence) 에 둔다.
 */
@ConfigurationProperties(prefix = "region-residence")
public record RegionResidenceRequirementProperties(int regulatedMonths, int metroMonths) {

    public RegionResidenceRequirementProperties {
        if (regulatedMonths <= 0) regulatedMonths = 24;
        if (metroMonths <= 0) metroMonths = 12;
    }

    public boolean isMetro(String regionName) {
        String r = regionName == null ? "" : regionName;
        return r.contains("서울") || r.contains("경기") || r.contains("인천");
    }
}
