package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 특별공급 소득·자산 요건 (도시근로자 월평균소득 대비 %, 자산 상한 원).
 *
 * 제도 개정으로 자주 바뀐다. 코드에 박지 않고 `application.yml` 로 둔다. 소득 상한은 <b>공고에 적용되는 법</b>에 따라 셋이다
 * ({@link IncomeRequirement#housingRuleOf}):
 * <ul>
 *   <li>{@code incomeLimit} — 공공주택 특별법 공공분양(공공주택 특별법 시행규칙 [별표 6]). 청약홈 특별법 표시 공고·LH 공고</li>
 *   <li>{@code nationalIncomeLimit} — 그 밖의 국민주택(주택공급에 관한 규칙). 비어 있으면 {@code incomeLimit}</li>
 *   <li>{@code privateIncomeLimit} — 민영주택(주택공급에 관한 규칙). 여기 없는 유형은 소득 기준이 없다
 *       (민영 다자녀 제40조·노부모 제46조). 비어 있으면 {@code incomeLimit}</li>
 *   <li>{@code realEstateLimit}·{@code realEstateTrack} — 민영·국민주택 신혼부부·생애최초·신생아는 소득 기준을 넘어도 세대 부동산
 *       가액이 이 금액 이하면 추첨공급 대상(제41조·제43조·제35조의3, 재산등급 29등급 하한·상한 평균 — 매년 바뀜)</li>
 *   <li>{@code birthBonus} — 공공주택 특별법: 2023. 3. 28. 이후 출생 자녀가 있으면 소득 기준에 %p 가산([별표 6] 제3호가목)</li>
 *   <li>{@code assetLimit} — 자산 상한(공공주택 특공에만 적용)</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "special-supply")
public record SpecialSupplyRequirementProperties(
        Map<SpecialSupplyType, IncomeLimit> incomeLimit,
        AssetLimit assetLimit,
        Map<SpecialSupplyType, IncomeLimit> privateIncomeLimit,
        Long realEstateLimit,
        Set<SpecialSupplyType> realEstateTrack,
        Map<SpecialSupplyType, IncomeLimit> nationalIncomeLimit,
        BirthBonus birthBonus) {

    @ConstructorBinding
    public SpecialSupplyRequirementProperties {
        incomeLimit = incomeLimit == null || incomeLimit.isEmpty() ? Map.of() : new EnumMap<>(incomeLimit);
        privateIncomeLimit = privateIncomeLimit == null || privateIncomeLimit.isEmpty() ? null : new EnumMap<>(privateIncomeLimit);
        nationalIncomeLimit = nationalIncomeLimit == null || nationalIncomeLimit.isEmpty() ? null : new EnumMap<>(nationalIncomeLimit);
        realEstateTrack = realEstateTrack == null || realEstateTrack.isEmpty()
                ? Set.of() : EnumSet.copyOf(realEstateTrack);
        if (birthBonus == null) birthBonus = new BirthBonus(0, 0);
    }

    /** 예전 두 칸짜리 — 민영·국민도 공공과 같은 상한, 부동산 추첨 경로·출생 가산 없음 */
    public SpecialSupplyRequirementProperties(Map<SpecialSupplyType, IncomeLimit> incomeLimit, AssetLimit assetLimit) {
        this(incomeLimit, assetLimit, null, null, null, null, null);
    }

    /** 소득 상한 — 도시근로자 대비 %. single: 단독, dual: 본인·배우자 모두 소득(맞벌이). */
    public record IncomeLimit(int single, int dual) {}

    /** 자산 상한 (원). 공공주택 특별공급에만 적용. */
    public record AssetLimit(long totalAssets, int carValue) {}

    /**
     * 2023. 3. 28. 이후 출생 자녀 가산(%p) — 공공주택 특별법 시행규칙 [별표 6] 제3호가목.
     * @param one       그 자녀만 1명
     * @param twoOrMore 그 자녀가 2명 이상, 또는 1명이고 그 전에 출생한 자녀도 있는 경우
     */
    public record BirthBonus(int one, int twoOrMore) {}

    /** 공공주택 특별법 공공분양 상한(%). 요건이 없는 유형이면 null. */
    public Integer incomeLimitPercent(SpecialSupplyType type, boolean dualIncome) {
        return pick(incomeLimit.get(type), dualIncome);
    }

    /** 민영주택 상한(%). 민영 표가 없으면 공공 기준, 민영 표에 없는 유형이면 null(소득 기준 없음). */
    public Integer privateIncomeLimitPercent(SpecialSupplyType type, boolean dualIncome) {
        if (privateIncomeLimit == null) return incomeLimitPercent(type, dualIncome);
        return pick(privateIncomeLimit.get(type), dualIncome);
    }

    /** 공공주택 특별법이 아닌 국민주택 상한(%). 표가 없으면 공공 기준 */
    public Integer nationalIncomeLimitPercent(SpecialSupplyType type, boolean dualIncome) {
        if (nationalIncomeLimit == null) return incomeLimitPercent(type, dualIncome);
        return pick(nationalIncomeLimit.get(type), dualIncome);
    }

    /** 소득 초과 시 부동산 가액으로 추첨공급 대상이 되는 유형인지(민영·국민주택) */
    public boolean hasRealEstateTrack(SpecialSupplyType type) {
        return realEstateLimit != null && realEstateTrack.contains(type);
    }

    private static Integer pick(IncomeLimit limit, boolean dualIncome) {
        if (limit == null) return null;
        return dualIncome ? limit.dual() : limit.single();
    }
}
