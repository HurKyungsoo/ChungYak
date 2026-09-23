package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.UnitType;

import java.time.LocalDate;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Objects;

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
        String priceLow,
        String priceHigh,
        String perPyeongLow,
        String perPyeongHigh,
        String noticeUrl,
        List<UnitRow> unitTypes,
        MatchInfo match) {

    public record UnitRow(String typeName, String supplyArea, String price, String pricePerPyeong,
                          int generalSupplyCount, int specialSupplyCount) {
        public static UnitRow of(UnitType u) {
            return new UnitRow(u.getTypeName(), u.getSupplyArea(),
                    Prices.formatManwon(u.getTopAmount()),
                    Prices.formatManwon(Prices.perPyeong(u.getTopAmount(), u.getSupplyArea())),
                    u.getGeneralSupplyCount(), u.getSpecialSupplyCount());
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

        // 분양가는 주택형마다 달라 한 칸에 담으려면 범위로 줄여야 한다 — 가장 싼 주택형과
        // 가장 비싼 주택형을 같이 보여준다. 하나뿐이거나 전부 같으면 화면이 한쪽만 그린다.
        IntSummaryStatistics prices = a.getUnitTypes().stream()
                .map(UnitType::getTopAmount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .summaryStatistics();
        boolean hasPrice = prices.getCount() > 0;

        // 평당가는 따로 센다 — 가장 싼 주택형이 평당가까지 가장 싼 건 아니다(면적이 다르므로).
        IntSummaryStatistics perPyeong = a.getUnitTypes().stream()
                .map(u -> Prices.perPyeong(u.getTopAmount(), u.getSupplyArea()))
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .summaryStatistics();
        boolean hasPerPyeong = perPyeong.getCount() > 0;

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
                YearMonths.format(a.getMoveInYearMonth()),
                hasPrice ? Prices.formatManwon(prices.getMin()) : null,
                hasPrice ? Prices.formatManwon(prices.getMax()) : null,
                hasPerPyeong ? Prices.formatManwon(perPyeong.getMin()) : null,
                hasPerPyeong ? Prices.formatManwon(perPyeong.getMax()) : null,
                a.getNoticeUrl(),
                a.getUnitTypes().stream().map(UnitRow::of).toList(),
                match);
    }
}
