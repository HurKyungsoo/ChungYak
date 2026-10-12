package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.domain.Announcement;

import java.time.LocalDate;

/**
 * 공고 목록 한 줄.
 *
 * 엔티티를 그대로 넘기면 화면에서 상태 계산·집계를 하게 되는데,
 * 그런 계산은 서버에서 끝내고 화면은 값만 그린다.
 *
 * LH 공고는 특별공급 유형별 세대수를 원천이 주지 않는다(countsKnown=false) — 합계 0 을
 * "특공 물량 없음"으로 그리면 거짓이 되므로 {@code lh} 로 갈라 특공 대신 금회 공급 세대수를 보여 준다.
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
        boolean ddayUrgent,
        String categoryLabel,
        String supplyTypeName,
        boolean lh,
        boolean waitlist,
        Integer totalSupplyCount) {

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
                dday.urgent(),
                a.getSupplyCategory() == null ? null : a.getSupplyCategory().getLabel(),
                a.getSupplyTypeName(),
                a.isLh(),
                a.isWaitlist(),
                a.getTotalSupplyCount());
    }
}
