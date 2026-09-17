package com.portfolio.chungyak.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 일반공급 가점제/추첨제 비율 계산 (B2b). 판정이 아니라 안내라 정답이 하나뿐이다 —
 * 전용면적 구간·규제 여부만으로 결정론적으로 갈린다.
 */
class GeneralSupplyLotteryCalculatorTest {

    private final GeneralSupplyLotteryCalculator calculator = new GeneralSupplyLotteryCalculator(
            new GeneralSupplyLotteryProperties(
                    new GeneralSupplyLotteryProperties.RegulatedPointRatio(40, 70, 80)));

    @Test
    @DisplayName("비규제지역은 전국 고정 비율이 없어 unknown 이 아니라 unregulated 로 구분한다")
    void unregulatedHasNoRatio() {
        var result = calculator.calculate("084.9730A", 100, false);

        assertThat(result.regulated()).isFalse();
        assertThat(result.known()).isFalse();
        assertThat(result.pointRatioPercent()).isNull();
    }

    @Test
    @DisplayName("전용 60㎡ 이하(경계 포함) — 가점 40% / 추첨 60%")
    void upTo60SqmBoundary() {
        var result = calculator.calculate("059.7400A", 100, true);

        assertThat(result.known()).isTrue();
        assertThat(result.pointRatioPercent()).isEqualTo(40);
        assertThat(result.lotteryRatioPercent()).isEqualTo(60);
        assertThat(result.lotteryUnits()).isEqualTo(60);
        assertThat(result.pointUnits()).isEqualTo(40);
    }

    @Test
    @DisplayName("전용 60㎡ 초과 85㎡ 이하(경계 포함) — 가점 70% / 추첨 30%")
    void between60And85SqmBoundary() {
        var result = calculator.calculate("085.0000B", 100, true);

        assertThat(result.pointRatioPercent()).isEqualTo(70);
        assertThat(result.lotteryRatioPercent()).isEqualTo(30);
    }

    @Test
    @DisplayName("전용 85㎡ 초과 — 가점 80% / 추첨 20%, 반올림해서 합이 전체 세대수와 같다")
    void over85Sqm() {
        var result = calculator.calculate("114.9500A", 141, true);

        assertThat(result.pointRatioPercent()).isEqualTo(80);
        assertThat(result.lotteryRatioPercent()).isEqualTo(20);
        assertThat(result.pointUnits() + result.lotteryUnits()).isEqualTo(141);
    }

    @Test
    @DisplayName("주택형 표기에서 숫자를 못 읽으면(형식 이상) unknown")
    void unparsableTypeNameIsUnknown() {
        var result = calculator.calculate("타입불명", 100, true);

        assertThat(result.regulated()).isTrue();
        assertThat(result.known()).isFalse();
    }

    @Test
    @DisplayName("일반공급 0세대여도 비율 자체는 계산된다(0/0으로 나눠도 됨)")
    void zeroGeneralSupplyStillComputesRatio() {
        var result = calculator.calculate("059.7400A", 0, true);

        assertThat(result.known()).isTrue();
        assertThat(result.pointUnits()).isZero();
        assertThat(result.lotteryUnits()).isZero();
    }
}
