package com.portfolio.chungyak.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Announcement#visibleSupplyTypes()} — 상세 화면 표에서 뺄 0-only 열 판단.
 */
class AnnouncementTest {

    private static UnitType unitType(SupplyBreakdown breakdown) {
        return UnitType.builder().modelNo("01").typeName("084.9730A").supplyBreakdown(breakdown).build();
    }

    @Test
    @DisplayName("어떤 주택형에도 배정이 없는 유형은 빠진다")
    void excludesTypesWithNoAllocationAnywhere() {
        Announcement a = Announcement.builder().build();
        a.addUnitType(unitType(SupplyBreakdown.builder().newlywed(47).build()));

        assertThat(a.visibleSupplyTypes())
                .containsExactly(SpecialSupplyType.NEWLYWED); // 다른 8개 유형은 전부 0
    }

    @Test
    @DisplayName("여러 주택형 중 하나라도 배정이 있으면 그 열은 보인다")
    void includesTypeAllocatedInAnyUnitType() {
        Announcement a = Announcement.builder().build();
        a.addUnitType(unitType(SupplyBreakdown.builder().newlywed(5).build()));
        a.addUnitType(unitType(SupplyBreakdown.builder().multiChild(3).build())); // 다른 타입엔 신혼 0

        assertThat(a.visibleSupplyTypes())
                .containsExactlyInAnyOrder(SpecialSupplyType.NEWLYWED, SpecialSupplyType.MULTI_CHILD);
    }

    @Test
    @DisplayName("순서는 SpecialSupplyType 선언 순서(기존 표 컬럼 순서)를 따른다")
    void preservesEnumDeclarationOrder() {
        Announcement a = Announcement.builder().build();
        // 선언 역순으로 넣어도 결과는 선언 순서
        a.addUnitType(unitType(SupplyBreakdown.builder().etc(1).newlywed(1).multiChild(1).build()));

        assertThat(a.visibleSupplyTypes())
                .containsExactly(SpecialSupplyType.MULTI_CHILD, SpecialSupplyType.NEWLYWED, SpecialSupplyType.ETC);
    }

    @Test
    @DisplayName("주택형이 없거나 배정이 전부 0이면 빈 목록")
    void emptyWhenNoAllocationAtAll() {
        Announcement a = Announcement.builder().build();

        assertThat(a.visibleSupplyTypes()).isEmpty();

        a.addUnitType(unitType(SupplyBreakdown.builder().build())); // 전부 기본값 0
        assertThat(a.visibleSupplyTypes()).isEmpty();
    }

    @Test
    @DisplayName("supplyBreakdown 이 null 인 주택형은 건너뛴다")
    void skipsUnitTypeWithNullBreakdown() {
        Announcement a = Announcement.builder().build();
        a.addUnitType(unitType(null));
        a.addUnitType(unitType(SupplyBreakdown.builder().youth(2).build()));

        assertThat(a.visibleSupplyTypes()).containsExactly(SpecialSupplyType.YOUTH);
    }
}
