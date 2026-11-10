package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.AnnouncementDocument;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.SupplyCategory;
import com.portfolio.chungyak.repository.AnnouncementDocumentRepository;
import com.portfolio.chungyak.rule.IncomeReference;
import com.portfolio.chungyak.rule.IncomeReferenceProperties;
import com.portfolio.chungyak.rule.NewlywedHopeTownProperties;
import com.portfolio.chungyak.rule.RuleTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 제도 금액 감시 — 공고문 금액이 그 공고 기준일의 설정과 다를 때만 알린다(판정은 그대로) */
class FigureWatchServiceTest {

    /** application.yml 2025년도 표와 같게 */
    private static final IncomeReference INCOME_2025 = new IncomeReference(new IncomeReferenceProperties("2025", Map.of(
            3, 7_533_763L, 4, 8_802_202L, 5, 9_326_985L, 6, 9_906_263L, 7, 10_485_541L, 8, 11_064_819L)));

    private static final String NOTICE_2026 = "도시근로자 가구당 월평균소득액의 100% 7,533,763 8,802,202 9,326,985 9,906,263 10,485,541 11,064,819 "
            + "자산 부동산 215,500천원 이하 자동차 45,420천원 이하";
    /** 새 해 기준이 실린 가상의 공고문 — 소득표·자산 모두 설정과 다르다 */
    private static final String NOTICE_NEXT_YEAR = "도시근로자 가구당 월평균소득액의 100% 7,800,000 9,100,000 9,600,000 10,200,000 10,800,000 11,400,000 "
            + "자산 부동산 225,000천원 이하 자동차 46,000천원 이하";

    private FigureWatchService serviceWith(Announcement a, String text) {
        AnnouncementQueryService qs = mock(AnnouncementQueryService.class);
        when(qs.findOpenOrUpcoming(any(), any(), any())).thenReturn(List.of(a));
        AnnouncementDocumentRepository docs = mock(AnnouncementDocumentRepository.class);
        when(docs.findByAnnouncementId(eq(1L))).thenReturn(Optional.of(new AnnouncementDocument(1L, "LH", text, Instant.EPOCH)));
        return new FigureWatchService(qs, docs, INCOME_2025, RuleTestSupport.REQUIREMENTS,
                new NewlywedHopeTownProperties(362_000_000L), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    private static Announcement publicSale(SupplyCategory category) {
        Announcement a = Announcement.builder().externalId("LH-1").houseName("시흥거모 S-1블록 공공분양주택")
                .houseType(HouseType.APT).houseDetailType(HouseDetailType.PUBLIC).supplyCategory(category)
                .noticeDate(LocalDate.of(2026, 10, 27)).build();
        ReflectionTestUtils.setField(a, "id", 1L);
        return a;
    }

    @Test
    @DisplayName("공고문 금액이 설정과 같으면 알림 없음")
    void matches() {
        var r = serviceWith(publicSale(SupplyCategory.SALE), NOTICE_2026).check();
        assertThat(r.announcementsChecked()).isEqualTo(1);
        assertThat(r.withNotice()).isEqualTo(1);
        assertThat(r.findings()).isEmpty();
    }

    @Test
    @DisplayName("새 해 금액이 실린 공고문이면 소득표·부동산·자동차를 '설정 갱신 필요'로 알린다")
    void detectsNewFigures() {
        var r = serviceWith(publicSale(SupplyCategory.SALE), NOTICE_NEXT_YEAR).check();
        assertThat(r.findings()).hasSize(3).allMatch(FigureWatchService.Finding::certain);
        assertThat(r.findings().get(0).figure()).contains("소득표").contains("2025");
        assertThat(r.findings().get(0).found()).contains("7,800,000");
    }

    @Test
    @DisplayName("임대 공고는 대조하지 않는다 — 소득표·자산 기준이 분양과 다르다")
    void skipsRental() {
        var r = serviceWith(publicSale(SupplyCategory.RENTAL), NOTICE_NEXT_YEAR).check();
        assertThat(r.announcementsChecked()).isZero();
        assertThat(r.findings()).isEmpty();
    }

    @Test
    @DisplayName("신혼희망타운 — 총자산 기준 금액이 공고문에 없으면 '확인 필요'")
    void hopeTownPresence() {
        Announcement hope = Announcement.builder().externalId("LH-1").houseName("남양주왕숙 A-2 신혼희망타운")
                .houseType(HouseType.NEWLYWED_HOPE_TOWN).houseDetailType(HouseDetailType.PUBLIC)
                .supplyCategory(SupplyCategory.HOPE_TOWN).noticeDate(LocalDate.of(2026, 11, 3)).build();
        ReflectionTestUtils.setField(hope, "id", 1L);

        assertThat(serviceWith(hope, "총자산 기준 362,000천원 이하").check().findings()).isEmpty();
        var r = serviceWith(hope, "총자산 기준 370,000천원 이하").check();
        assertThat(r.findings()).singleElement().satisfies(f -> {
            assertThat(f.certain()).isFalse();
            assertThat(f.figure()).contains("신혼희망타운");
        });
    }
}
