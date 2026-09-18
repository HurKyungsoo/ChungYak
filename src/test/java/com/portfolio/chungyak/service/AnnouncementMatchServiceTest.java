package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.*;
import com.portfolio.chungyak.repository.AnnouncementRepository;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.rule.RuleTestSupport;
import com.portfolio.chungyak.web.view.MatchRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * "내 조건으로 공고 찾기" — 접수중·접수예정 공고를 전부 순회해 {@link EligibilityEngine} 으로
 * 실제 판정하고, 신청 가능한 것만 남긴다는 걸 검증한다. 판정 자체는 엔진이 이미 하므로
 * 여기서는 "필터링·정렬만 하고 자격을 새로 따지지 않는다"만 확인한다.
 */
class AnnouncementMatchServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 9, 18).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    private EligibilityEngine engine;
    private AnnouncementMatchService matchService;

    @BeforeEach
    void setUp() {
        engine = new EligibilityEngine(RuleTestSupport.allRules());
    }

    @Test
    @DisplayName("자격 되는 유형이 하나라도 있고 물량이 배정된 공고만 결과에 남는다")
    void onlyKeepsAnnouncementsWithActualMatch() {
        Announcement matches = announcement("신혼부부만 되는 공고", LocalDate.now(CLOCK).plusDays(10));
        matches.addUnitType(unitType("084A", SupplyBreakdown.builder().newlywed(30).build()));

        Announcement noAllocation = announcement("특공 물량 없는 공고", LocalDate.now(CLOCK).plusDays(5));
        noAllocation.addUnitType(unitType("059A", SupplyBreakdown.builder().build()));

        matchService = serviceWith(matches, noAllocation);

        List<MatchRow> rows = matchService.findMatching(newlywedProfile());

        assertThat(rows).extracting(MatchRow::houseName).containsExactly("신혼부부만 되는 공고");
        assertThat(rows.get(0).matchedTypeLabels()).contains("신혼부부");
    }

    @Test
    @DisplayName("마감이 가까운 공고부터 정렬된다")
    void sortsByClosestDeadlineFirst() {
        Announcement later = announcement("늦게 마감", LocalDate.now(CLOCK).plusDays(20));
        later.addUnitType(unitType("084A", SupplyBreakdown.builder().newlywed(10).build()));

        Announcement sooner = announcement("빨리 마감", LocalDate.now(CLOCK).plusDays(3));
        sooner.addUnitType(unitType("084A", SupplyBreakdown.builder().newlywed(10).build()));

        matchService = serviceWith(later, sooner);

        List<MatchRow> rows = matchService.findMatching(newlywedProfile());

        assertThat(rows).extracting(MatchRow::houseName).containsExactly("빨리 마감", "늦게 마감");
    }

    private AnnouncementMatchService serviceWith(Announcement... announcements) {
        AnnouncementRepository repo = mock(AnnouncementRepository.class);
        when(repo.findOpenWithUnitTypes(any())).thenReturn(List.of(announcements));
        AnnouncementQueryService qs = new AnnouncementQueryService(repo, CLOCK);
        return new AnnouncementMatchService(qs, engine);
    }

    private static ApplicantProfile newlywedProfile() {
        return RuleTestSupport.passingIncomeAndAssets()
                .married(true)
                .monthsSinceMarriage(24)
                .houseless(true)
                .accountMonths(30)
                .build();
    }

    private Announcement announcement(String name, LocalDate receptEnd) {
        return Announcement.builder()
                .externalId(name)
                .houseManageNo("1")
                .pblancNo("1")
                .houseName(name)
                .houseType(HouseType.APT)
                .houseDetailType(HouseDetailType.PRIVATE)
                .regionName("서울")
                .receptBeginDate(receptEnd.minusDays(2))
                .receptEndDate(receptEnd)
                .regulationFlags(RegulationFlags.builder().build())
                .build();
    }

    private UnitType unitType(String typeName, SupplyBreakdown breakdown) {
        return UnitType.builder()
                .modelNo(typeName)
                .typeName(typeName)
                .generalSupplyCount(100)
                .specialSupplyCount(breakdown.total())
                .supplyBreakdown(breakdown)
                .topAmount(87000)
                .build();
    }
}
