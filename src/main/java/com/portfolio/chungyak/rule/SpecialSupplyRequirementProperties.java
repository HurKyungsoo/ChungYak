package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * 특별공급 소득·자산 요건 (도시근로자 월평균소득 대비 %, 자산 상한 원).
 *
 * 제도 개정으로 자주 바뀐다. 코드에 박지 않고 `application.yml` 로 둔다.
 * 유형별 소득 상한(단독/맞벌이), 자산 상한(공공주택 특공에만 적용).
 */
@ConfigurationProperties(prefix = "special-supply")
public record SpecialSupplyRequirementProperties(
        Map<SpecialSupplyType, IncomeLimit> incomeLimit,
        AssetLimit assetLimit) {

    public SpecialSupplyRequirementProperties {
        incomeLimit = incomeLimit == null ? Map.of() : new EnumMap<>(incomeLimit);
    }

    /** 소득 상한 — 도시근로자 대비 %. single: 단독, dual: 맞벌이 완화. */
    public record IncomeLimit(int single, int dual) {}

    /** 자산 상한 (원). 공공주택 특별공급에만 적용. */
    public record AssetLimit(long totalAssets, int carValue) {}

    /** 유형·맞벌이 여부에 맞는 소득 상한(%). 요건이 없는 유형이면 null. */
    public Integer incomeLimitPercent(SpecialSupplyType type, boolean dualIncome) {
        IncomeLimit limit = incomeLimit.get(type);
        if (limit == null) {
            return null;
        }
        return dualIncome ? limit.dual() : limit.single();
    }
}
