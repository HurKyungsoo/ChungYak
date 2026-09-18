package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;
import com.portfolio.chungyak.rule.EligibilityEngine.UnitMatch;

import java.time.LocalDate;
import java.util.List;

/**
 * "내 조건으로 공고 찾기" 결과 한 줄.
 *
 * {@link MatchResult} 는 이미 rule 패키지가 낸 결정론적 판정이고, 이 레코드는 그 결과를
 * 화면에 옮겨 담기만 한다 — 여기서 자격을 다시 따지지 않는다.
 */
public record MatchRow(
        Long id,
        String houseName,
        String regionName,
        String houseDetailTypeLabel,
        LocalDate receptBeginDate,
        LocalDate receptEndDate,
        String status,
        String ddayLabel,
        boolean ddayUrgent,
        List<String> matchedTypeLabels,
        String bestUnitTypeName,
        boolean allocationCountKnown,
        int totalAllocated) {

    public static MatchRow of(MatchResult result, String status, LocalDate today) {
        Announcement a = result.announcement();
        Dday dday = Dday.of(status, a.getReceptBeginDate(), a.getReceptEndDate(), today);
        List<String> types = result.matches().stream()
                .flatMap(m -> m.applicableTypes().stream())
                .map(t -> t.getLabel())
                .distinct()
                .toList();
        UnitMatch best = result.bestMatch();

        return new MatchRow(
                a.getId(),
                a.getHouseName(),
                a.getRegionName(),
                a.getHouseDetailType() == null ? "-" : a.getHouseDetailType().getLabel(),
                a.getReceptBeginDate(),
                a.getReceptEndDate(),
                status,
                dday.label(),
                dday.urgent(),
                types,
                best == null ? null : best.unitType().getTypeName(),
                best != null && best.allocationCountKnown(),
                best == null ? 0 : best.totalAllocated());
    }
}
