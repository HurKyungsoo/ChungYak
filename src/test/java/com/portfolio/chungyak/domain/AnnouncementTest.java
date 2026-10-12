package com.portfolio.chungyak.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

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

    // === 총 공급세대수 폴백 (LH 는 원천이 총계를 안 준다) ===

    private static UnitType counted(int general, int special) {
        return UnitType.builder().modelNo("01").typeName("26A")
                .generalSupplyCount(general).specialSupplyCount(special).build();
    }

    @Test
    @DisplayName("총 세대수가 없으면 주택형 세대수(일반+특별) 합계로 채운다")
    void fillsTotalFromUnitTypesWhenMissing() {
        Announcement a = Announcement.builder().build();
        a.addUnitType(counted(30, 0));
        a.addUnitType(counted(12, 5));

        a.fillTotalSupplyCountFromUnitTypes();

        assertThat(a.getTotalSupplyCount()).isEqualTo(47);
    }

    @Test
    @DisplayName("원천이 준 총 세대수는 덮어쓰지 않는다")
    void keepsTotalFromSource() {
        Announcement a = Announcement.builder().totalSupplyCount(500).build();
        a.addUnitType(counted(30, 0));

        a.fillTotalSupplyCountFromUnitTypes();

        assertThat(a.getTotalSupplyCount()).isEqualTo(500);
    }

    @Test
    @DisplayName("주택형이 없거나 합계가 0 이면 모름(null)으로 둔다 — 0세대로 단정하지 않는다")
    void staysNullWhenNoCounts() {
        Announcement a = Announcement.builder().build();
        a.fillTotalSupplyCountFromUnitTypes();
        assertThat(a.getTotalSupplyCount()).isNull();

        a.addUnitType(counted(0, 0));   // 예비입주자 모집 — 금회 공급 0
        a.fillTotalSupplyCountFromUnitTypes();
        assertThat(a.getTotalSupplyCount()).isNull();
    }

    @Test
    @DisplayName("재수집이 총계를 null 로 보내도 붙어 있는 주택형으로 다시 채운다")
    void updateFromExternalRefillsFromExistingUnitTypes() {
        Announcement a = Announcement.builder().houseName("하남").build();
        a.addUnitType(counted(40, 0));

        a.updateFromExternal("하남", "경기도", null, null, null, null, null, null, null, null, null);

        assertThat(a.getTotalSupplyCount()).isEqualTo(40);
    }

    @Test
    @DisplayName("재수집(목록만)이 null 로 보낸 상세 값은 지우지 않는다 — 마감일은 목록 값으로 갱신")
    void updateFromExternalKeepsDetailValuesWhenNull() {
        LocalDate begin = LocalDate.of(2026, 10, 12);
        LocalDate winner = LocalDate.of(2027, 1, 28);
        Announcement a = Announcement.builder().houseName("하남")
                .address("경기도 하남시 감일순환로 40").receptBeginDate(begin)
                .receptEndDate(LocalDate.of(2026, 10, 14)).winnerAnnounceDate(winner)
                .noticePdfUrl("https://apply.lh.or.kr/lhapply/lhFile.do?fileid=68802346").build();

        LocalDate newEnd = LocalDate.of(2026, 10, 15);   // 정정공고로 마감 연장
        a.updateFromExternal("하남", "경기도", null, null, null, newEnd, null, null,
                SupplyCategory.HOPE_TOWN, "행복주택(신혼희망)", null);

        assertThat(a.getAddress()).isEqualTo("경기도 하남시 감일순환로 40");
        assertThat(a.getReceptBeginDate()).isEqualTo(begin);
        assertThat(a.getWinnerAnnounceDate()).isEqualTo(winner);
        assertThat(a.getNoticePdfUrl()).endsWith("68802346");
        assertThat(a.getReceptEndDate()).isEqualTo(newEnd);
        assertThat(a.getSupplyCategory()).isEqualTo(SupplyCategory.HOPE_TOWN);
    }
}
