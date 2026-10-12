package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.domain.Announcement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AnnouncementIcsTest {

    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 12, 3, 0, 0, 0, ZoneOffset.UTC);

    @Test
    @DisplayName("공고에 있는 날짜만 일정으로 — 계약 등 모르는 날짜는 만들지 않는다")
    void onlyKnownDates() {
        Announcement a = Announcement.builder()
                .specialReceptBeginDate(LocalDate.of(2026, 10, 12)).specialReceptEndDate(LocalDate.of(2026, 10, 12))
                .receptBeginDate(LocalDate.of(2026, 10, 13)).receptEndDate(LocalDate.of(2026, 10, 14))
                .build();

        assertThat(AnnouncementIcs.events(a))
                .extracting(AnnouncementIcs.Event::title, AnnouncementIcs.Event::start, AnnouncementIcs.Event::end)
                .containsExactly(
                        tuple("특별공급 접수", LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12)),
                        tuple("청약 접수", LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14)));
    }

    @Test
    @DisplayName("시작일을 모르면 기간을 지어내지 않고 마감일 하루짜리 '마감' 일정")
    void endOnly() {
        Announcement a = Announcement.builder().receptEndDate(LocalDate.of(2026, 10, 20)).build();
        assertThat(AnnouncementIcs.events(a))
                .extracting(AnnouncementIcs.Event::title, AnnouncementIcs.Event::start)
                .containsExactly(tuple("청약 접수 마감", LocalDate.of(2026, 10, 20)));
    }

    @Test
    @DisplayName("날짜가 하나도 없으면 일정 없음 — 화면이 버튼을 숨긴다")
    void noDates() {
        assertThat(AnnouncementIcs.events(Announcement.builder().build())).isEmpty();
    }

    @Test
    @DisplayName("종일 일정의 DTEND 는 마지막 날 다음 날, 공고명 쉼표는 이스케이프, 줄은 CRLF·75옥텟 이하")
    void renderRfc5545() {
        Announcement a = Announcement.builder().houseName("더샵 동인센트리체, 1단지; 아주 긴 이름의 아파트 단지 공고명")
                .receptBeginDate(LocalDate.of(2026, 10, 13)).receptEndDate(LocalDate.of(2026, 10, 14))
                .build();

        String ics = new String(AnnouncementIcs.render(a, "https://example.test/announcements/11", NOW),
                StandardCharsets.UTF_8);

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("DTSTART;VALUE=DATE:20261013\r\n", "DTEND;VALUE=DATE:20261015\r\n",
                "DTSTAMP:20261012T030000Z\r\n");
        // 접힌 줄을 펴서 내용 확인
        String unfolded = ics.replace("\r\n ", "");
        assertThat(unfolded).contains("SUMMARY:[청약 접수] 더샵 동인센트리체\\, 1단지\\; 아주 긴 이름의 아파트 단지 공고명");
        for (String line : ics.split("\r\n")) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).as(line).isLessThanOrEqualTo(75);
        }
    }
}
