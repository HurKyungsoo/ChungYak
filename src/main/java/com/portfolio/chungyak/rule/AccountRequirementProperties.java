package com.portfolio.chungyak.rule;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 청약통장 납입횟수·예치금 기준.
 *
 * - 국민주택(공공): 납입 횟수 (1순위 기준). 규제지역은 더 많이 필요.
 * - 민영주택: 지역별 예치금 (전용 85㎡ 이하 기준). 큰 평형은 더 필요 — 화면에 고지.
 *
 * 제도·고시로 바뀌므로 `application.yml`(account) 에 둔다.
 */
@ConfigurationProperties(prefix = "account")
public record AccountRequirementProperties(
        int publicMinCount,
        int publicMinCountRegulated,
        PrivateDeposit privateDeposit) {

    public AccountRequirementProperties {
        if (publicMinCount <= 0) publicMinCount = 12;
        if (publicMinCountRegulated <= 0) publicMinCountRegulated = 24;
        if (privateDeposit == null) privateDeposit = new PrivateDeposit(3_000_000, 2_500_000, 2_000_000);
    }

    /** 지역 등급별 예치금 (원). tier1: 서울·부산, tier2: 그 외 광역시, tier3: 기타 시·군 */
    public record PrivateDeposit(int tier1, int tier2, int tier3) {}

    public int publicMinCount(boolean regulated) {
        return regulated ? publicMinCountRegulated : publicMinCount;
    }

    /** 지역명 → 필요 예치금(원). 전용 85㎡ 이하 기준. */
    public int depositFor(String regionName) {
        String r = regionName == null ? "" : regionName;
        if (r.contains("서울") || r.contains("부산")) {
            return privateDeposit.tier1();
        }
        if (r.contains("대구") || r.contains("인천") || r.contains("광주")
                || r.contains("대전") || r.contains("울산")) {
            return privateDeposit.tier2();
        }
        return privateDeposit.tier3();
    }
}
