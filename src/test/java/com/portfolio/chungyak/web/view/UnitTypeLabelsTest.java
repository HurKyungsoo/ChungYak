package com.portfolio.chungyak.web.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnitTypeLabelsTest {

    @Test
    @DisplayName("주택형 — 전용면적 정수 부분 + 접미사, 반올림하지 않는다")
    void shortName() {
        assertThat(UnitTypeLabels.shortName("084.9730A")).isEqualTo("84A");
        assertThat(UnitTypeLabels.shortName("059.9800")).isEqualTo("59");
        assertThat(UnitTypeLabels.shortName("074.9845T")).isEqualTo("74T");
        assertThat(UnitTypeLabels.shortName("26A")).isEqualTo("26A");
        assertThat(UnitTypeLabels.shortName("주거형 오피스텔")).isEqualTo("주거형 오피스텔");
        assertThat(UnitTypeLabels.shortened("26A")).isFalse();
        assertThat(UnitTypeLabels.shortened("084.9730A")).isTrue();
    }

    @Test
    @DisplayName("면적 — 소수 둘째 자리까지, 끝의 0 은 뗀다")
    void area() {
        assertThat(UnitTypeLabels.area("109.5711")).isEqualTo("109.57");
        assertThat(UnitTypeLabels.area("38.50")).isEqualTo("38.5");
        assertThat(UnitTypeLabels.area(null)).isNull();
        assertThat(UnitTypeLabels.area("미정")).isEqualTo("미정");
    }
}
