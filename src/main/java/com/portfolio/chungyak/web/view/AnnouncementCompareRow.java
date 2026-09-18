package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.UnitType;

import java.time.LocalDate;
import java.util.List;

/**
 * 공고 비교 화면(찜한 공고 여러 개를 나란히) 한 열.
 *
 * 스크랩은 이 브라우저의 localStorage 에만 있어 서버는 어떤 공고가 찜됐는지 전혀 모른다 —
 * 화면이 JS 로 찜한 id 목록을 모아 {@code ?ids=1,2,3} 으로 요청하면, 그 id들만 조회해서
 * 나란히 보여줄 뿐이다.
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
        List<UnitRow> unitTypes) {

    public record UnitRow(String typeName, String supplyArea, int generalSupplyCount, int specialSupplyCount) {
        public static UnitRow of(UnitType u) {
            return new UnitRow(u.getTypeName(), u.getSupplyArea(), u.getGeneralSupplyCount(), u.getSpecialSupplyCount());
        }
    }

    public static AnnouncementCompareRow of(Announcement a, String status, LocalDate today) {
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
                a.getMoveInYearMonth(),
                a.getNoticeUrl(),
                a.getUnitTypes().stream().map(UnitRow::of).toList());
    }
}
