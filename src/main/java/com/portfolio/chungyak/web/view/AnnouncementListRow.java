package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.domain.Announcement;

import java.time.LocalDate;

/**
 * 공고 목록 한 줄.
 *
 * 엔티티를 그대로 넘기면 화면에서 상태 계산·집계를 하게 되는데,
 * 그런 계산은 서버에서 끝내고 화면은 값만 그린다.
 */
public record AnnouncementListRow(
        Long id,
        String houseName,
        String regionName,
        String houseDetailTypeLabel,
        LocalDate receptBeginDate,
        LocalDate receptEndDate,
        String status,
        int unitTypeCount,
        int totalSpecialSupply,
        String ddayLabel,
        boolean ddayUrgent) {

    public static AnnouncementListRow of(Announcement a, String status, LocalDate today) {
        int specialSum = a.getUnitTypes().stream()
                .mapToInt(u -> u.getSupplyBreakdown() == null ? 0 : u.getSupplyBreakdown().total())
                .sum();
        Dday dday = Dday.of(status, a.getReceptBeginDate(), a.getReceptEndDate(), today);
        return new AnnouncementListRow(
                a.getId(),
                a.getHouseName(),
                a.getRegionName(),
                a.getHouseDetailType() == null ? "-" : a.getHouseDetailType().getLabel(),
                a.getReceptBeginDate(),
                a.getReceptEndDate(),
                status,
                a.getUnitTypes().size(),
                specialSum,
                dday.label(),
                dday.urgent());
    }
}
