package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.mapper.CompetitionMapper;
import com.portfolio.chungyak.mapper.CompetitionMapper.PastSpecialSupply;
import com.portfolio.chungyak.service.SpecialSupplyCompareService.Row;
import com.portfolio.chungyak.web.view.EligibilityResultView;
import com.portfolio.chungyak.web.view.EligibilityResultView.TypeDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 충족 유형이 여럿일 때 "어느 유형으로" 참고 표 — 과거 기록 집계만, 판정은 다시 하지 않는다 */
class SpecialSupplyCompareServiceTest {

    private final CompetitionMapper mapper = mock(CompetitionMapper.class);
    private final SpecialSupplyCompareService service = new SpecialSupplyCompareService(
            mapper, Clock.fixed(LocalDate.of(2026, 11, 10).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul")));

    private static Announcement announcement(String externalId) {
        return Announcement.builder()
                .externalId(externalId).houseManageNo("H").pblancNo("H").houseName("t")
                .houseType(HouseType.APT).houseDetailType(HouseDetailType.PRIVATE)
                .regionName("서울").noticeDate(LocalDate.of(2026, 10, 30))
                .build();
    }

    private static TypeDecision decision(String type, String label, boolean eligible, Integer allocated) {
        return new TypeDecision(type, label, eligible, false, List.of("ok"), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), allocated, false);
    }

    private static EligibilityResultView view(TypeDecision... ds) {
        return new EligibilityResultView(1L, "t", false, true, List.of(), List.of(), List.of(ds));
    }

    private static PastSpecialSupply past(String house, String type, int households, int applications) {
        return new PastSpecialSupply(house, type, households, applications);
    }

    @Test
    @DisplayName("충족 유형마다 과거 공고별 '신청 ÷ 배정' 중앙값·범위를 내고, 가장 낮은 유형을 표시한다")
    void comparesMedians() {
        when(mapper.pastSpecialSupplies(eq("서울"), eq("PRIVATE"), eq(LocalDate.of(2025, 10, 30)), eq(LocalDate.of(2026, 10, 30))))
                .thenReturn(List.of(
                        past("A", "NEWLYWED", 10, 100), past("B", "NEWLYWED", 10, 300), past("C", "NEWLYWED", 10, 50),
                        past("A", "FIRST_TIME", 10, 400), past("B", "FIRST_TIME", 10, 600), past("C", "FIRST_TIME", 10, 200),
                        past("D", "FIRST_TIME", 0, 30)));   // 배정 0 은 배수를 못 낸다 — 뺀다

        var c = service.compare(announcement("X"), view(
                decision("NEWLYWED", "신혼부부", true, 25),
                decision("FIRST_TIME", "생애최초", true, 21),
                decision("MULTI_CHILD", "다자녀가구", false, 16))).orElseThrow();

        assertThat(c.rows()).extracting(Row::typeName).containsExactly("NEWLYWED", "FIRST_TIME");   // 미충족 유형은 빠진다
        Row newlywed = c.rows().get(0);
        assertThat(newlywed.allocated()).isEqualTo(25);
        assertThat(newlywed.median()).isEqualTo(10.0);   // 5, 10, 30
        assertThat(newlywed.min()).isEqualTo(5.0);
        assertThat(newlywed.max()).isEqualTo(30.0);
        assertThat(newlywed.samples()).isEqualTo(3);
        assertThat(newlywed.lowest()).isTrue();
        assertThat(c.rows().get(1).median()).isEqualTo(40.0);
        assertThat(c.rows().get(1).samples()).isEqualTo(3);
        assertThat(c.rows().get(1).lowest()).isFalse();
        assertThat(c.region()).isEqualTo("서울");
        assertThat(c.scope()).isEqualTo("민영");
    }

    @Test
    @DisplayName("과거 공고가 3개 미만이면 중앙값을 내지 않고 '가장 낮음'도 붙이지 않는다")
    void tooFewSamples() {
        when(mapper.pastSpecialSupplies(any(), any(), any(), any())).thenReturn(List.of(
                past("A", "NEWLYWED", 10, 100), past("B", "NEWLYWED", 10, 300), past("C", "NEWLYWED", 10, 50),
                past("A", "FIRST_TIME", 10, 400)));

        var c = service.compare(announcement("X"), view(
                decision("NEWLYWED", "신혼부부", true, 25), decision("FIRST_TIME", "생애최초", true, 21))).orElseThrow();

        assertThat(c.rows().get(1).median()).isNull();
        assertThat(c.rows().get(1).samples()).isEqualTo(1);
        assertThat(c.rows()).noneMatch(Row::lowest);   // 비교할 상대가 하나뿐
    }

    @Test
    @DisplayName("충족 유형이 하나뿐이거나 LH 공고·배정 미상이면 표를 만들지 않는다")
    void notShownWhenNothingToChoose() {
        assertThat(service.compare(announcement("X"), view(
                decision("NEWLYWED", "신혼부부", true, 25), decision("FIRST_TIME", "생애최초", false, 21)))).isEmpty();
        assertThat(service.compare(announcement("LH-1"), view(
                decision("NEWLYWED", "신혼부부", true, 25), decision("FIRST_TIME", "생애최초", true, 21)))).isEmpty();
        assertThat(service.compare(announcement("X"), view(
                decision("NEWLYWED", "신혼부부", true, null), decision("FIRST_TIME", "생애최초", true, 21)))).isEmpty();
        verify(mapper, never()).pastSpecialSupplies(any(), any(), any(), any());
    }

    @Test
    @DisplayName("충족 유형 어느 것도 지난 기록이 없으면(수집 전) 표를 만들지 않는다")
    void noHistoryAtAll() {
        when(mapper.pastSpecialSupplies(any(), any(), any(), any())).thenReturn(List.of());
        assertThat(service.compare(announcement("X"), view(
                decision("NEWLYWED", "신혼부부", true, 25), decision("FIRST_TIME", "생애최초", true, 21)))).isEmpty();
    }

    @Test
    @DisplayName("기록 조회가 실패해도 결과 화면은 표 없이 나온다")
    void mapperFailureHidesTable() {
        when(mapper.pastSpecialSupplies(any(), any(), any(), any())).thenThrow(new RuntimeException("db down"));
        assertThat(service.compare(announcement("X"), view(
                decision("NEWLYWED", "신혼부부", true, 25), decision("FIRST_TIME", "생애최초", true, 21)))).isEmpty();
    }
}
