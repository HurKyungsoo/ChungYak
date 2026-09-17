package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.repository.AnnouncementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 목록 필터(지역·주택유형·공고명 검색) 조합이 의도대로 걸러지는지 확인한다.
 */
class AnnouncementQueryServiceTest {

    private AnnouncementRepository repository;
    private AnnouncementQueryService service;
    private final LocalDate today = LocalDate.of(2026, 9, 17);

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(AnnouncementRepository.class);
        Clock clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        service = new AnnouncementQueryService(repository, clock);
    }

    private Announcement announcement(String houseName, String region) {
        return Announcement.builder()
                .externalId("E-" + houseName).houseManageNo("H").pblancNo("P")
                .houseName(houseName).houseType(HouseType.APT).houseDetailType(HouseDetailType.PRIVATE)
                .regionName(region)
                .receptBeginDate(today).receptEndDate(today.plusDays(3))
                .build();
    }

    @Test
    @DisplayName("공고명 키워드는 대소문자 구분 없이 부분 일치한다")
    void keywordMatchesCaseInsensitiveSubstring() {
        when(repository.findOpenWithUnitTypes(today)).thenReturn(List.of(
                announcement("힐스테이트 고덕아이파크", "서울"),
                announcement("e편한세상 검단역", "인천")));

        List<Announcement> result = service.findOpenOrUpcoming(null, null, "힐스테이트");

        assertThat(result).extracting(Announcement::getHouseName).containsExactly("힐스테이트 고덕아이파크");
    }

    @Test
    @DisplayName("영문 대소문자를 구분하지 않는다")
    void keywordIgnoresCase() {
        when(repository.findOpenWithUnitTypes(today)).thenReturn(List.of(
                announcement("e편한세상 검단역", "인천")));

        assertThat(service.findOpenOrUpcoming(null, null, "E편한세상")).hasSize(1);
    }

    @Test
    @DisplayName("키워드가 비어 있으면 전부 반환한다")
    void blankKeywordReturnsAll() {
        when(repository.findOpenWithUnitTypes(today)).thenReturn(List.of(
                announcement("A", "서울"), announcement("B", "부산")));

        assertThat(service.findOpenOrUpcoming(null, null, "  ")).hasSize(2);
    }

    @Test
    @DisplayName("지역·키워드 필터를 동시에 적용한다")
    void combinesRegionAndKeyword() {
        when(repository.findOpenWithUnitTypes(today)).thenReturn(List.of(
                announcement("힐스테이트 서울", "서울"),
                announcement("힐스테이트 부산", "부산")));

        List<Announcement> result = service.findOpenOrUpcoming("부산", null, "힐스테이트");

        assertThat(result).extracting(Announcement::getHouseName).containsExactly("힐스테이트 부산");
    }
}
