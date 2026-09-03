package com.portfolio.chungyak.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가구 월평균소득 -> 도시근로자 대비 % 환산 검증.
 */
class IncomeReferenceTest {

    private static IncomeReference ref() {
        Map<Integer, Long> table = new LinkedHashMap<>();
        table.put(1, 3_482_964L);
        table.put(2, 5_415_712L);
        table.put(3, 7_198_649L);
        table.put(4, 8_248_467L);
        table.put(7, 10_351_493L);
        table.put(8, 11_139_704L);
        return new IncomeReference(new IncomeReferenceProperties("2024", table));
    }

    @Test
    @DisplayName("기준 소득과 같으면 100%")
    void exactlyBaseIs100() {
        assertThat(ref().percentOf(8_248_467, 4)).isEqualTo(100);
    }

    @Test
    @DisplayName("반올림해서 % 계산")
    void roundsToPercent() {
        // 10,000,000 / 8,248,467 * 100 = 121.23... -> 121
        assertThat(ref().percentOf(10_000_000, 4)).isEqualTo(121);
        // 5,000,000 / 8,248,467 * 100 = 60.6... -> 61
        assertThat(ref().percentOf(5_000_000, 4)).isEqualTo(61);
    }

    @Test
    @DisplayName("입력이 부족하면 null (추측 안 함)")
    void missingInputIsNull() {
        assertThat(ref().percentOf(null, 4)).isNull();
        assertThat(ref().percentOf(6_000_000, null)).isNull();
        assertThat(ref().percentOf(6_000_000, 0)).isNull();
    }

    @Test
    @DisplayName("표에 없는 가구원 수는 하위 구간을 쓴다 (5·6인 -> 4인 기준)")
    void gapUsesLowerBracket() {
        assertThat(ref().baseFor(5)).isEqualTo(8_248_467L);
        assertThat(ref().baseFor(6)).isEqualTo(8_248_467L);
    }

    @Test
    @DisplayName("표 최대치를 넘는 가구는 마지막 구간 증가분으로 연장")
    void beyondTableExtendsLinearly() {
        // 8인 11,139,704 + (8인-7인=788,211) * 1 = 11,927,915
        assertThat(ref().baseFor(9)).isEqualTo(11_927_915L);
    }

    @Test
    @DisplayName("기준표가 비어 있으면 null")
    void emptyTableIsNull() {
        IncomeReference empty = new IncomeReference(new IncomeReferenceProperties("2024", Map.of()));
        assertThat(empty.percentOf(6_000_000, 3)).isNull();
    }
}
