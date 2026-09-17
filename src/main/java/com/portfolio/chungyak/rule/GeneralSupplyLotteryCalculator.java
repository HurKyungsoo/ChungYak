package com.portfolio.chungyak.rule;

import org.springframework.stereotype.Component;

/**
 * 민영주택 일반공급의 가점제/추첨제 세대수 분리 — 판정이 아니라 안내다(B2b).
 *
 * 순수 계산이다. "이 유형에 자격이 되는가"를 다루는 EligibilityRule 과 달리
 * 자격과 무관하게 "일반공급 물량 중 몇 세대가 추첨으로 뽑히는가"만 계산한다.
 *
 * 출처: 주택공급에 관한 규칙 제28조, 국토교통부 정책브리핑(2022-12-14, 2023년 시행)
 * — 투기과열지구·청약과열지역 한정, 전용 60㎡ 이하 가점 40%, 60~85㎡ 70%, 85㎡ 초과 80%.
 * 비규제지역은 지자체 공고로 정해져 전국 고정 비율이 없다 — 지어내지 않고 안내만 한다.
 */
@Component
public class GeneralSupplyLotteryCalculator {

    private final GeneralSupplyLotteryProperties properties;

    public GeneralSupplyLotteryCalculator(GeneralSupplyLotteryProperties properties) {
        this.properties = properties;
    }

    /**
     * @param typeName          "084.9730A" 같은 주택형 표기 — 숫자 부분이 전용면적(㎡)이다.
     * @param generalSupplyCount 이 주택형의 일반공급 세대수
     * @param regulated         공고의 투기과열지구·청약과열지역 여부
     */
    public Result calculate(String typeName, int generalSupplyCount, boolean regulated) {
        if (!regulated) {
            return Result.unregulated();
        }
        Double exclusiveArea = parseExclusiveAreaSqm(typeName);
        if (exclusiveArea == null) {
            return Result.unknown();
        }

        int pointRatio = properties.pointRatioPercent(exclusiveArea);
        int lotteryRatio = 100 - pointRatio;
        int lotteryUnits = Math.round(generalSupplyCount * lotteryRatio / 100f);
        int pointUnits = generalSupplyCount - lotteryUnits;
        return new Result(true, pointRatio, lotteryRatio, pointUnits, lotteryUnits);
    }

    /** "084.9730A" -> 84.973. 형식이 다르면(숫자 없음 등) null. */
    static Double parseExclusiveAreaSqm(String typeName) {
        if (typeName == null) return null;
        String digits = typeName.replaceAll("[^0-9.]", "");
        if (digits.isBlank()) return null;
        try {
            double value = Double.parseDouble(digits);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * regulated=false 면 지자체 공고 확인 필요(전국 고정 비율 없음) — ratio·세대수 필드는 null.
     * known=false 면 면적을 못 읽거나 일반공급 세대수가 0이라 계산 자체를 못 한 것.
     */
    public record Result(boolean regulated, Integer pointRatioPercent, Integer lotteryRatioPercent,
                          Integer pointUnits, Integer lotteryUnits) {

        private static final Result UNREGULATED = new Result(false, null, null, null, null);
        private static final Result UNKNOWN = new Result(true, null, null, null, null);

        static Result unregulated() { return UNREGULATED; }
        static Result unknown() { return UNKNOWN; }

        public boolean known() { return pointRatioPercent != null; }
    }
}
