package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.SupplyCategory;
import com.portfolio.chungyak.mapper.CompetitionMapper.PastUnit;
import com.portfolio.chungyak.service.CompetitionStatsService.BandStat;
import com.portfolio.chungyak.service.CompetitionStatsService.Summary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 지역 최근 당첨 기록 집계 (#34) — 순수 함수 부분. */
class CompetitionStatsServiceTest {

    private static final LocalDate NOTICE = LocalDate.of(2026, 10, 1);

    private static PastUnit unit(String house, LocalDate date, String type, int lowest, String rate) {
        return new PastUnit(house, date, type, lowest, rate);
    }

    @Test
    @DisplayName("면적대 — 전용 60 이하 / 60~85 / 85 초과")
    void band() {
        assertThat(CompetitionStatsService.band("059.9800A")).isEqualTo("60㎡ 이하");
        assertThat(CompetitionStatsService.band("084.9955A")).isEqualTo("60~85㎡");
        assertThat(CompetitionStatsService.band("127.8267B")).isEqualTo("85㎡ 초과");
        assertThat(CompetitionStatsService.band("타입")).isNull();
    }

    @Test
    @DisplayName("공고별 최저가점(면적대 주택형 중 최솟값)의 범위·중앙값, 0 은 뺀다, 경쟁률 쉼표·미달 처리")
    void summarizesPerNotice() {
        List<PastUnit> past = List.of(
                unit("A", NOTICE.minusMonths(2), "084.9A", 52, "10.50"),
                unit("A", NOTICE.minusMonths(2), "084.9B", 48, "8.00"),     // A 의 최저 = 48, 경쟁률 중앙값 9.25
                unit("B", NOTICE.minusMonths(5), "084.9A", 0, "(△3)"),      // 가점 0(미달) — 가점 표본에서 빠지고 미달로 센다
                unit("B", NOTICE.minusMonths(5), "084.9B", 61, "1,234.50"),
                unit("C", NOTICE.minusMonths(9), "075.1A", 33, "-"),
                unit("D", NOTICE.minusMonths(3), "059.9A", 40, "3.00"));    // 다른 면적대

        Summary s = CompetitionStatsService.summarize("경기", Set.of("60~85㎡"), past, NOTICE);

        assertThat(s.bands()).hasSize(1);
        BandStat b = s.bands().get(0);
        assertThat(b.band()).isEqualTo("60~85㎡");
        assertThat(b.months()).isEqualTo(12);
        assertThat(b.total()).isEqualTo(3);
        assertThat(b.notices()).isEqualTo(3);
        assertThat(b.minLowest()).isEqualTo(33);
        assertThat(b.medianLowest()).isEqualTo(48);
        assertThat(b.maxLowest()).isEqualTo(61);
        assertThat(b.medianRate()).isEqualTo((9.25 + 1234.5) / 2);   // 공고별 중앙값(A 9.25, B 1,234.5)의 중앙값
        assertThat(b.undersubscribed()).isEqualTo(1);
    }

    @Test
    @DisplayName("12개월에 3 공고가 안 되면 24개월로, 그래도 안 되면 그 면적대는 뺀다")
    void fallsBackThenDrops() {
        List<PastUnit> past = List.of(
                unit("A", NOTICE.minusMonths(2), "084.9A", 50, "5"),
                unit("B", NOTICE.minusMonths(14), "084.9A", 40, "5"),
                unit("C", NOTICE.minusMonths(20), "084.9A", 45, "5"),
                unit("D", NOTICE.minusMonths(30), "084.9A", 45, "5"),       // 24개월 밖
                unit("E", NOTICE.minusMonths(1), "120.1A", 70, "5"));       // 85 초과는 1건뿐 → 뺀다

        Summary s = CompetitionStatsService.summarize("서울", Set.of("60~85㎡", "85㎡ 초과"), past, NOTICE);

        assertThat(s.bands()).singleElement().satisfies(b -> {
            assertThat(b.months()).isEqualTo(24);
            assertThat(b.notices()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("표본이 하나도 안 되면 비어 있다 — 화면은 카드를 그리지 않는다")
    void emptyWhenNoSample() {
        assertThat(CompetitionStatsService.summarize("서울", Set.of("60~85㎡"), List.of(), NOTICE).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("청약홈 민영 분양 공고만 — 국민주택(가점제 없음)·LH·무순위는 보지 않는다")
    void eligibility() {
        Announcement.AnnouncementBuilder base = Announcement.builder().id(1L).externalId("APT-1").houseManageNo("1").pblancNo("1")
                .houseName("공고").houseType(HouseType.APT).regionName("서울").noticeDate(NOTICE);
        assertThat(CompetitionStatsService.eligible(base.houseDetailType(HouseDetailType.PRIVATE).build())).isTrue();
        assertThat(CompetitionStatsService.eligible(base.houseDetailType(HouseDetailType.PUBLIC).build())).isFalse();
        assertThat(CompetitionStatsService.eligible(base.houseDetailType(HouseDetailType.PRIVATE)
                .supplyCategory(SupplyCategory.REMAINDER).build())).isFalse();
        assertThat(CompetitionStatsService.eligible(base.houseDetailType(HouseDetailType.PRIVATE).supplyCategory(null)
                .externalId("LH-1").build())).isFalse();
    }
}
