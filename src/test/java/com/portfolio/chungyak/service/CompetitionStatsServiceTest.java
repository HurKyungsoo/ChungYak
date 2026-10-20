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
        return new PastUnit(house, date, type, lowest, rate, null, null);
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
                unit("B", NOTICE.minusMonths(5), "084.9A", 0, "(△3)"),      // 미달 + 다른 주택형은 경쟁 → "경쟁 있던" 쪽에만
                unit("B", NOTICE.minusMonths(5), "084.9B", 61, "1,234.50"),
                unit("C", NOTICE.minusMonths(9), "075.1A", 33, "-"),
                unit("D", NOTICE.minusMonths(3), "059.9A", 40, "3.00"),     // 다른 면적대
                unit("E", NOTICE.minusMonths(4), "084.9A", 0, "(△2)"));      // 전부 미달 — 가점 표본에서 빠지고 미달로 센다

        Summary s = CompetitionStatsService.summarize("경기", Set.of("60~85㎡"), past, NOTICE);

        assertThat(s.bands()).hasSize(1);
        BandStat b = s.bands().get(0);
        assertThat(b.band()).isEqualTo("60~85㎡");
        assertThat(b.months()).isEqualTo(12);
        assertThat(b.total()).isEqualTo(4);
        assertThat(b.notices()).isEqualTo(3);
        assertThat(b.minLowest()).isEqualTo(33);
        assertThat(b.medianLowest()).isEqualTo(48);
        assertThat(b.maxLowest()).isEqualTo(61);
        assertThat(b.lowsCsv()).isEqualTo("33,48,61");   // 화면이 저장된 내 가점과 견주는 값 — 오름차순, 0 뺀 공고별 최저
        assertThat(b.medianRate()).isEqualTo((9.25 + 1234.5) / 2);   // 공고별 중앙값(A 9.25, B 1,234.5)의 중앙값
        assertThat(b.rateNotices()).isEqualTo(2);
        assertThat(b.undersubscribed()).isEqualTo(1);   // E 만 — B 는 경쟁 있던 공고에 센다
    }

    @Test
    @DisplayName("미달은 1순위 최종 결과로 — 해당지역만 모자라고 기타지역으로 다 채운 주택형은 미달이 아니다")
    void undersubscribedUsesFinalFirstRank() {
        PastUnit filledByOthers = new PastUnit("A", NOTICE, "084.9A", 0, "(△1)", "14.00", null);
        PastUnit stillShort = new PastUnit("B", NOTICE, "084.9A", 0, "(△30)", "(△23)", null);
        PastUnit gyeonggi = new PastUnit("C", NOTICE, "084.9A", 0, "(△5)", "-", "(△2)");   // 경기: 기타경기 단계에서도 모자람
        PastUnit localOnly = new PastUnit("D", NOTICE, "084.9A", 0, "(△3)", null, null);   // 기타지역 행이 없으면 해당지역 값

        assertThat(CompetitionStatsService.finalUnder(filledByOthers)).isFalse();
        assertThat(CompetitionStatsService.finalUnder(stillShort)).isTrue();
        assertThat(CompetitionStatsService.finalUnder(gyeonggi)).isTrue();
        assertThat(CompetitionStatsService.finalUnder(localOnly)).isTrue();
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
    @DisplayName("비슷한 최근 공고 — 같은 시군구 먼저, 그다음 평당가가 가까운 순, 기록 없는 공고는 뺀다")
    void similarNotices() {
        Announcement current = Announcement.builder().id(9L).externalId("APT-9").houseManageNo("9").pblancNo("9")
                .houseName("이 공고").houseType(HouseType.APT).houseDetailType(HouseDetailType.PRIVATE)
                .regionName("경기").address("경기도 화성시 동탄").noticeDate(NOTICE).build();
        // 공급면적 112.4㎡(34평) × 최고 분양가 68,000만원 → 평당 2,000만원
        current.addUnitType(com.portfolio.chungyak.domain.UnitType.builder().modelNo("01").typeName("084.9A")
                .supplyArea("112.4").topAmount(68_000).generalSupplyCount(100).specialSupplyCount(50).build());

        List<PastUnit> past = List.of(
                unit("A", NOTICE.minusMonths(1), "084.9A", 50, "12.00"),     // 수원시, 평당 ~2,000
                unit("B", NOTICE.minusMonths(2), "084.9A", 0, "(△5)"),       // 화성시(같은 시군구), 평당 ~3,000
                unit("C", NOTICE.minusMonths(3), "084.9A", 41, "3.50"),      // 평택시, 평당 ~1,500
                unit("D", NOTICE.minusMonths(4), "084.9A", 60, "20.00"));    // 용인시, 평당 ~2,050
        List<com.portfolio.chungyak.mapper.CompetitionMapper.PastNoticeUnit> units = List.of(
                pnu("A", "수원 단지", "경기도 수원시 영통구", NOTICE.minusMonths(1), 68_000),
                pnu("B", "화성 단지", "경기도 화성시 봉담읍", NOTICE.minusMonths(2), 102_000),
                pnu("C", "평택 단지", "경기도 평택시 고덕", NOTICE.minusMonths(3), 51_000),
                pnu("D", "용인 단지", "경기도 용인시 처인구", NOTICE.minusMonths(4), 69_700),
                pnu("E", "기록 없는 단지", "경기도 화성시", NOTICE.minusMonths(1), 68_000));

        var got = CompetitionStatsService.similar(current, "60~85㎡", past, units);

        assertThat(got).extracting(CompetitionStatsService.SimilarNotice::name).containsExactly("화성 단지", "수원 단지", "용인 단지");
        assertThat(got.get(0).lowest()).isNull();            // 가점 없음(미달) → 화면은 "추첨·미달"
        assertThat(got.get(0).rate()).isEqualTo("미달");
        assertThat(got.get(1).lowest()).isEqualTo(50);
        assertThat(got.get(1).rate()).isEqualTo("12.0 : 1");
        assertThat(got.get(1).area()).isEqualTo("수원시");
    }

    private static com.portfolio.chungyak.mapper.CompetitionMapper.PastNoticeUnit pnu(String house, String name, String address,
                                                                                    LocalDate date, int topAmount) {
        return new com.portfolio.chungyak.mapper.CompetitionMapper.PastNoticeUnit(house, name, address, date, "084.9A", "112.4", topAmount);
    }

    @Test
    @DisplayName("가점 비교 — 공고별 최저가점 중 내 가점 이하인 공고 수, 0(미달·추첨)은 빼고, 3건 미만이면 24개월로")
    void compareScore() {
        List<PastUnit> past = List.of(
                unit("A", NOTICE.minusMonths(1), "084.9A", 48, "5"),
                unit("A", NOTICE.minusMonths(1), "084.9B", 52, "5"),     // A 의 최저 = 48
                unit("B", NOTICE.minusMonths(4), "084.9A", 61, "5"),
                unit("C", NOTICE.minusMonths(6), "084.9A", 33, "5"),
                unit("D", NOTICE.minusMonths(2), "084.9A", 0, "(△1)"),   // 가점 없음 — 빠진다
                unit("E", NOTICE.minusMonths(3), "059.9A", 20, "5"));    // 다른 면적대

        var c = CompetitionStatsService.compare("경기", "60~85㎡", 48, past, NOTICE).orElseThrow();

        assertThat(c.months()).isEqualTo(12);
        assertThat(c.notices()).isEqualTo(3);
        assertThat(c.atOrBelow()).isEqualTo(2);   // 33, 48
        assertThat(c.minLowest()).isEqualTo(33);
        assertThat(c.medianLowest()).isEqualTo(48);
        assertThat(c.maxLowest()).isEqualTo(61);

        assertThat(CompetitionStatsService.compare("경기", "85㎡ 초과", 48, past, NOTICE)).isEmpty();
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
