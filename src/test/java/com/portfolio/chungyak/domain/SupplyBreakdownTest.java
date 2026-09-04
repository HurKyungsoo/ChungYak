package com.portfolio.chungyak.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SupplyBreakdownTest {

    @Test
    @DisplayName("청약홈 경로 — 세대수를 알고, total 이 합계다")
    void knownCounts() {
        SupplyBreakdown b = SupplyBreakdown.builder()
                .newlywed(47).multiChild(31).firstTime(22)
                .build();

        assertThat(b.isCountsKnown()).isTrue();
        assertThat(b.countOf(SpecialSupplyType.NEWLYWED)).isEqualTo(47);
        assertThat(b.hasAllocation(SpecialSupplyType.NEWLYWED)).isTrue();
        assertThat(b.total()).isEqualTo(100);
    }

    @Test
    @DisplayName("LH 경로 — ofPresentTypes 는 유형 존재만, countsKnown=false, total=0")
    void presentTypesOnly() {
        SupplyBreakdown b = SupplyBreakdown.ofPresentTypes(
                Set.of(SpecialSupplyType.NEWLYWED, SpecialSupplyType.NEWBORN));

        assertThat(b.isCountsKnown()).isFalse();
        assertThat(b.hasAllocation(SpecialSupplyType.NEWLYWED)).isTrue();
        assertThat(b.hasAllocation(SpecialSupplyType.NEWBORN)).isTrue();
        assertThat(b.hasAllocation(SpecialSupplyType.MULTI_CHILD)).isFalse();
        assertThat(b.total()).isZero();   // 세대수 미상이라 합계는 의미 없음
        assertThat(b.countOf(SpecialSupplyType.NEWLYWED)).isEqualTo(1);  // 존재 플래그
    }

    @Test
    @DisplayName("빈 집합이면 어떤 유형도 배정 없음")
    void emptyPresentTypes() {
        SupplyBreakdown b = SupplyBreakdown.ofPresentTypes(Set.of());
        assertThat(b.isCountsKnown()).isFalse();
        for (SpecialSupplyType t : SpecialSupplyType.values()) {
            assertThat(b.hasAllocation(t)).isFalse();
        }
    }
}
