package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.UnitType;

import java.time.LocalDate;
import java.util.List;

/**
 * 공고 비교 화면(여러 공고를 나란히) 한 열.
 *
 * 찜한 공고도 오늘 본 공고도 이 브라우저의 localStorage 에만 있어 서버는 무엇을 고른 건지
 * 전혀 모른다 — 화면이 JS 로 id 목록을 모아 {@code ?ids=1,2,3} 으로 요청하면, 그 id들만
 * 조회해서 나란히 보여줄 뿐이다.
 */
public record AnnouncementCompareRow(
        Long id,
        String houseName,
        String regionName,
        String houseDetailTypeLabel,
        String houseTypeLabel,
        boolean regulated,
        LocalDate receptBeginDate,
        LocalDate receptEndDate,
        String status,
        String ddayLabel,
        boolean ddayUrgent,
        Integer totalSupplyCount,
        int unitTypeCount,
        int totalSpecialSupply,
        String moveInYearMonth,
        String noticeUrl,
        List<UnitRow> unitTypes,
        MatchInfo match) {

    public record UnitRow(String typeName, String supplyArea, int generalSupplyCount, int specialSupplyCount) {
        public static UnitRow of(UnitType u) {
            return new UnitRow(u.getTypeName(), u.getSupplyArea(), u.getGeneralSupplyCount(), u.getSpecialSupplyCount());
        }
    }

    /**
     * 저장된 조건으로 이 공고를 판정한 결과 — {@code null} 이면 조건을 적용하지 않은 상태다.
     *
     * 규칙 엔진이 낸 사실만 담는다. <b>"어느 공고가 더 유리한가"는 담지 않는다</b> —
     * 경쟁률 데이터가 없어서 배정 세대수가 많다고 당첨 가능성이 높다고 말할 수 없기 때문이다.
     * 판단 재료(신청 가능한 유형·주택형 수·배정 세대수)만 나란히 놓고, 고르는 건 사용자 몫이다.
     */
    public record MatchInfo(
            boolean applicable,
            List<String> typeLabels,
            int matchedUnitTypeCount,
            boolean allocationCountKnown,
            int totalAllocated) {}

    public static AnnouncementCompareRow of(Announcement a, String status, LocalDate today) {
        return of(a, status, today, null);
    }

    public static AnnouncementCompareRow of(Announcement a, String status, LocalDate today, MatchInfo match) {
        Dday dday = Dday.of(status, a.getReceptBeginDate(), a.getReceptEndDate(), today);
        int specialSum = a.getUnitTypes().stream()
                .mapToInt(u -> u.getSupplyBreakdown() == null ? 0 : u.getSupplyBreakdown().total())
                .sum();

        return new AnnouncementCompareRow(
                a.getId(),
                a.getHouseName(),
                a.getRegionName(),
                a.getHouseDetailType() == null ? "-" : a.getHouseDetailType().getLabel(),
                a.getHouseType() == null ? "-" : a.getHouseType().getLabel(),
                a.getRegulationFlags() != null && a.getRegulationFlags().isRegulatedArea(),
                a.getReceptBeginDate(),
                a.getReceptEndDate(),
                status,
                dday.label(),
                dday.urgent(),
                a.getTotalSupplyCount(),
                a.getUnitTypes().size(),
                specialSum,
                formatMoveInYearMonth(a.getMoveInYearMonth()),
                a.getNoticeUrl(),
                a.getUnitTypes().stream().map(UnitRow::of).toList(),
                match);
    }

    /** 원본은 "202910" 같은 YYYYMM 문자열이다 — 화면 표시용으로만 "2029-10"으로 바꾼다. */
    private static String formatMoveInYearMonth(String raw) {
        if (raw == null || !raw.matches("\\d{6}")) return raw;
        return raw.substring(0, 4) + "-" + raw.substring(4);
    }
}
