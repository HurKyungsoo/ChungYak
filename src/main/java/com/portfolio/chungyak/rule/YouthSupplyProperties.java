package com.portfolio.chungyak.rule;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.LocalDate;
import java.util.List;

/**
 * 청년 특별공급(공공주택 특별법 공공분양) 기준 — {@link com.portfolio.chungyak.rule.rules.YouthRule}.
 *
 * 출처: 2026-10 청약홈 공공분양 입주자모집공고 2건(제주 삼도이동 토지임대부 2026000435 · 경기 광교 지분적립형 2026000527) 원문 —
 * "19세 이상~39세 이하로 혼인 중이 아니며, 과거 주택을 소유한 사실이 없는 무주택자", 입주자저축 6개월·6회,
 * 본인 소득 ≤ 도시근로자 <b>1인 가구</b> 월평균소득 140%(5,338,708원), 총자산 본인 276백만원·부모 1,035백만원 이하
 * (2023-03-28 이후 출생 자녀 1명이면 본인 311백만원, 2명 이상이면 345백만원). 두 공고가 같은 금액이다.
 * 마이홈포털 안내(270,000천원·1,011,000천원)는 이전 해 값이다.
 *
 * 금액은 해마다 바뀐다 — 새 해 값은 덮어쓰지 말고 지금 값을 {@code previous} 로 옮긴 뒤 {@code applies-from} 을 적는다({@link YearlyFigure}).
 */
@ConfigurationProperties(prefix = "youth-supply")
public record YouthSupplyProperties(int ageMin, int ageMax, int incomePercent, Amounts amounts) {

    @ConstructorBinding
    public YouthSupplyProperties {
        if (amounts != null) YearlyFigure.of(amounts.figure(), amounts.previous().stream().map(Amounts::figure).toList());
    }

    /**
     * 해마다 바뀌는 금액 한 벌.
     *
     * @param singlePersonMonthlyAverage 전년도 도시근로자 1인 가구 월평균소득 100%(원) — 청년은 3인 이하 합친 값이 아니라 1인 값을 쓴다
     */
    public record Amounts(long singlePersonMonthlyAverage, long selfAssetLimit, long parentsAssetLimit,
                          long birthOneSelfAssetLimit, long birthTwoOrMoreSelfAssetLimit,
                          String basis, LocalDate appliesFrom, List<Amounts> previous) {

        @ConstructorBinding
        public Amounts {
            previous = previous == null ? List.of() : List.copyOf(previous);
        }

        YearlyFigure<Amounts> figure() {
            return new YearlyFigure<>(this, basis, appliesFrom);
        }

        /** 출생 가산 구분(0·1·2, {@link IncomeRequirement#birthTier})별 본인 총자산 상한 */
        public long selfAssetLimit(int birthTier) {
            return birthTier >= 2 ? birthTwoOrMoreSelfAssetLimit : birthTier == 1 ? birthOneSelfAssetLimit : selfAssetLimit;
        }
    }

    /** 공고일에 맞는 금액 버전 */
    public YearlyFigure.Pick<Amounts> amountsFor(LocalDate noticeDate) {
        return YearlyFigure.pick(YearlyFigure.of(amounts.figure(), amounts.previous().stream().map(Amounts::figure).toList()), noticeDate);
    }
}
