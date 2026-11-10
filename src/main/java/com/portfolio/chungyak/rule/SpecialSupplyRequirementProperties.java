package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
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
 *   <li>{@code assetLimit} — 부동산·자동차 상한(공공주택 특공에만 적용), 출생 자녀 완화 금액 포함</li>
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
        BirthBonus birthBonus,
        /** 부동산 추첨 기준 금액의 기준 이름·적용 시작 공고일·예전 값 — {@link YearlyFigure} */
        String realEstateLimitBasis,
        LocalDate realEstateLimitAppliesFrom,
        List<AmountVersion> realEstateLimitPrevious) {

    @ConstructorBinding
    public SpecialSupplyRequirementProperties {
        incomeLimit = incomeLimit == null || incomeLimit.isEmpty() ? Map.of() : new EnumMap<>(incomeLimit);
        privateIncomeLimit = privateIncomeLimit == null || privateIncomeLimit.isEmpty() ? null : new EnumMap<>(privateIncomeLimit);
        nationalIncomeLimit = nationalIncomeLimit == null || nationalIncomeLimit.isEmpty() ? null : new EnumMap<>(nationalIncomeLimit);
        realEstateTrack = realEstateTrack == null || realEstateTrack.isEmpty()
                ? Set.of() : EnumSet.copyOf(realEstateTrack);
        if (birthBonus == null) birthBonus = new BirthBonus(0, 0);
        realEstateLimitPrevious = realEstateLimitPrevious == null ? List.of() : List.copyOf(realEstateLimitPrevious);
        if (realEstateLimit != null) {   // 설정 검증 — 어긋나면 기동 때 실패
            YearlyFigure.of(new YearlyFigure<>(realEstateLimit, realEstateLimitBasis, realEstateLimitAppliesFrom),
                    realEstateLimitPrevious.stream().map(AmountVersion::toFigure).toList());
        }
    }

    /** 부동산 추첨 기준 버전 없이(시작일 미확인 한 벌) */
    public SpecialSupplyRequirementProperties(Map<SpecialSupplyType, IncomeLimit> incomeLimit, AssetLimit assetLimit,
                                              Map<SpecialSupplyType, IncomeLimit> privateIncomeLimit, Long realEstateLimit,
                                              Set<SpecialSupplyType> realEstateTrack,
                                              Map<SpecialSupplyType, IncomeLimit> nationalIncomeLimit, BirthBonus birthBonus) {
        this(incomeLimit, assetLimit, privateIncomeLimit, realEstateLimit, realEstateTrack, nationalIncomeLimit, birthBonus,
                null, null, null);
    }

    /** 예전 두 칸짜리 — 민영·국민도 공공과 같은 상한, 부동산 추첨 경로·출생 가산 없음 */
    public SpecialSupplyRequirementProperties(Map<SpecialSupplyType, IncomeLimit> incomeLimit, AssetLimit assetLimit) {
        this(incomeLimit, assetLimit, null, null, null, null, null, null, null, null);
    }

    /** 예전 금액 한 벌 — 원 단위 금액 하나짜리 기준(부동산 추첨 기준 등) */
    public record AmountVersion(long amount, String basis, LocalDate appliesFrom) {
        YearlyFigure<Long> toFigure() {
            return new YearlyFigure<>(amount, basis, appliesFrom);
        }
    }

    /** 공고일에 맞는 부동산 추첨 기준 금액. {@link #hasRealEstateTrack} 이 참일 때만 부른다 */
    public YearlyFigure.Pick<Long> realEstateLimitFor(LocalDate noticeDate) {
        return YearlyFigure.pick(YearlyFigure.of(new YearlyFigure<>(realEstateLimit, realEstateLimitBasis, realEstateLimitAppliesFrom),
                realEstateLimitPrevious.stream().map(AmountVersion::toFigure).toList()), noticeDate);
    }

    /** 공고일에 맞는 공공 특공 자산 기준. 자산 기준이 없으면 null */
    public YearlyFigure.Pick<AssetLimit> assetLimitFor(LocalDate noticeDate) {
        if (assetLimit == null) return null;
        return YearlyFigure.pick(YearlyFigure.of(new YearlyFigure<>(assetLimit, assetLimit.basis(), assetLimit.appliesFrom()),
                assetLimit.previous().stream().map(p -> new YearlyFigure<>(p, p.basis(), p.appliesFrom())).toList()), noticeDate);
    }

    /** 소득 상한 — 도시근로자 대비 %. single: 단독, dual: 본인·배우자 모두 소득(맞벌이). */
    public record IncomeLimit(int single, int dual) {}

    /**
     * 자산 상한 (원). 공공주택 특별공급(다자녀·노부모·신혼부부·생애최초·신생아)에만 적용 — 공공주택 특별법 시행규칙
     * 제13조③에 따라 국토부가 해마다 정한다. 2023. 3. 28. 이후 출생 자녀가 있으면 완화 금액(birthOne·birthTwoOrMore).
     */
    public record AssetLimit(long realEstate, int carValue, AssetAmounts birthOne, AssetAmounts birthTwoOrMore,
                             /** 기준 이름("2026년도")·적용 시작 공고일·예전 기준 — {@link YearlyFigure} */
                             String basis, LocalDate appliesFrom, List<AssetLimit> previous) {

        @ConstructorBinding
        public AssetLimit {
            previous = previous == null ? List.of() : List.copyOf(previous);
        }

        /** 버전 없이(시작일 미확인 한 벌) */
        public AssetLimit(long realEstate, int carValue, AssetAmounts birthOne, AssetAmounts birthTwoOrMore) {
            this(realEstate, carValue, birthOne, birthTwoOrMore, null, null, null);
        }

        /** 출생 가산 구분(0 없음, 1 그 자녀만 1명, 2 그 밖) — {@link IncomeRequirement#birthTier}. 완화 금액이 없으면 기본 금액 */
        public AssetAmounts forBirthTier(int tier) {
            AssetAmounts relaxed = tier >= 2 ? birthTwoOrMore : tier == 1 ? birthOne : null;
            return relaxed != null ? relaxed : new AssetAmounts(realEstate, carValue);
        }
    }

    /** 부동산(토지+건축물)·자동차 상한 (원) */
    public record AssetAmounts(long realEstate, int carValue) {}

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
