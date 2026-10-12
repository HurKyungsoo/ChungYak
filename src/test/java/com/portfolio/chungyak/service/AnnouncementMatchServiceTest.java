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

    @Test
    @DisplayName("목록 배지: 가능 / 기간만 채우면 가능(엔진 힌트 포함) / 정보 부족 / 물량 모름 — 엔진 결과만 옮긴다")
    void listBadges() {
        Announcement newlywed = announcement("신혼부부 공고", LocalDate.now(CLOCK).plusDays(10));
        newlywed.addUnitType(unitType("084A", SupplyBreakdown.builder().newlywed(30).build()));
        Announcement hopeTownTotalOnly = announcement("유형별 물량 모름", LocalDate.now(CLOCK).plusDays(10));
        hopeTownTotalOnly.addUnitType(unitType("055A", SupplyBreakdown.builder().build()));
        setId(newlywed, 1L);
        setId(hopeTownTotalOnly, 2L);
        matchService = serviceWith(newlywed, hopeTownTotalOnly);

        var ok = matchService.badges(newlywedProfile());
        assertThat(ok.get(1L).state()).isEqualTo(com.portfolio.chungyak.web.view.ListBadge.State.ELIGIBLE);
        assertThat(ok.get(1L).types()).containsExactly("신혼부부");
        assertThat(ok.get(2L).state()).isEqualTo(com.portfolio.chungyak.web.view.ListBadge.State.UNKNOWN);

        // 통장 3개월(비규제 6개월 요건) — 기간만 채우면 되는 미충족이라 FIXABLE, 엔진이 남긴 힌트가 따라온다
        var shortAccount = matchService.badges(RuleTestSupport.passingIncomeAndAssets()
                .married(true).monthsSinceMarriage(24).houseless(true).accountMonths(3).build());
        assertThat(shortAccount.get(1L).state()).isEqualTo(com.portfolio.chungyak.web.view.ListBadge.State.FIXABLE);
        assertThat(shortAccount.get(1L).hints()).isNotEmpty();

        // 빠른 진단처럼 소득·통장을 안 넣은 조건 — "해당 없음"이 아니라 "정보 더 필요"
        var partial = matchService.badges(ApplicantProfile.builder().married(true).monthsSinceMarriage(24).houseless(true).build());
        assertThat(partial.get(1L).state()).isEqualTo(com.portfolio.chungyak.web.view.ListBadge.State.NEEDS_INFO);
    }

    @Test
    @DisplayName("이것만 바뀌면 가능: 메울 수 있는 미충족만 남은 공고를 엔진 힌트 문장별로 묶는다 (이미 가능한 공고·되돌릴 수 없는 미충족은 제외)")
    void fixableGroups() {
        Announcement a = announcement("A 공고", LocalDate.now(CLOCK).plusDays(10));
        a.addUnitType(unitType("084A", SupplyBreakdown.builder().newlywed(30).build()));
        Announcement b = announcement("B 공고", LocalDate.now(CLOCK).plusDays(12));
        b.addUnitType(unitType("084A", SupplyBreakdown.builder().newlywed(10).build()));
        setId(a, 1L);
        setId(b, 2L);
        matchService = serviceWith(a, b);

        // 통장 3개월 — 두 공고 모두 같은 요건(비규제 6개월)이라 한 묶음, 2곳
        var groups = matchService.fixableGroups(RuleTestSupport.passingIncomeAndAssets()
                .married(true).monthsSinceMarriage(24).houseless(true).accountMonths(3).build());
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).count()).isEqualTo(2);
        assertThat(groups.get(0).hints()).isNotEmpty();

        // 이미 신청 가능하면 묶음에 오지 않는다
        assertThat(matchService.fixableGroups(newlywedProfile())).isEmpty();
        // 혼인 기간 초과(되돌릴 수 없음)는 "이것만 바뀌면"이 아니다
        assertThat(matchService.fixableGroups(RuleTestSupport.passingIncomeAndAssets()
                .married(true).monthsSinceMarriage(120).houseless(true).accountMonths(3).build())).isEmpty();
    }

    private static void setId(Announcement a, Long id) {
        org.springframework.test.util.ReflectionTestUtils.setField(a, "id", id);
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
