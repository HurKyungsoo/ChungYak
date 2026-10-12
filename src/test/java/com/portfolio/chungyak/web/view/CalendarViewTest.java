package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarViewTest {

    private Announcement announcement(Long id, String name, LocalDate begin, LocalDate end) {
        return Announcement.builder()
                .id(id)
                .externalId("ext-" + id)
                .houseManageNo("H" + id)
                .pblancNo("P" + id)
                .houseName(name)
                .houseType(HouseType.APT)
                .houseDetailType(HouseDetailType.PRIVATE)
                .receptBeginDate(begin)
                .receptEndDate(end)
                .build();
    }

    @Test
    void gridHasSixFullWeeks() {
        CalendarView view = CalendarView.of(List.of(), YearMonth.of(2026, 9), LocalDate.of(2026, 9, 1));

        assertThat(view.weeks()).hasSize(6);
        view.weeks().forEach(week -> assertThat(week).hasSize(7));
        // 2026-09-01 은 화요일 -> 그리드는 일요일(08-30)부터 시작
        assertThat(view.weeks().get(0).get(0).date()).isEqualTo(LocalDate.of(2026, 8, 30));
    }

    @Test
    void flagsDaysOutsideMonthAndToday() {
        CalendarView view = CalendarView.of(List.of(), YearMonth.of(2026, 9), LocalDate.of(2026, 9, 10));

        CalendarDay aug30 = view.weeks().get(0).get(0);
        assertThat(aug30.inMonth()).isFalse();

        CalendarDay sep10 = view.weeks().stream()
                .flatMap(List::stream)
                .filter(d -> d.date().equals(LocalDate.of(2026, 9, 10)))
                .findFirst().orElseThrow();
        assertThat(sep10.inMonth()).isTrue();
        assertThat(sep10.today()).isTrue();
    }

    @Test
    void placesStartAndEndEventsOnTheirDates() {
        Announcement a = announcement(1L, "테스트 아파트",
                LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16));

        CalendarView view = CalendarView.of(List.of(a), YearMonth.of(2026, 9), LocalDate.of(2026, 9, 1));

        CalendarDay start = dayOf(view, LocalDate.of(2026, 9, 14));
        assertThat(start.events()).hasSize(1);
        assertThat(start.events().get(0).kind()).isEqualTo(CalendarEvent.Kind.START);
        assertThat(start.events().get(0).houseName()).isEqualTo("테스트 아파트");

        CalendarDay end = dayOf(view, LocalDate.of(2026, 9, 16));
        assertThat(end.events()).hasSize(1);
        assertThat(end.events().get(0).kind()).isEqualTo(CalendarEvent.Kind.END);

        CalendarDay untouched = dayOf(view, LocalDate.of(2026, 9, 15));
        assertThat(untouched.events()).isEmpty();
    }

    @Test
    void navigationMonthsAreAdjacentAndCurrentMonthFlagIsAccurate() {
        CalendarView view = CalendarView.of(List.of(), YearMonth.of(2026, 9), LocalDate.of(2026, 9, 1));

        assertThat(view.prevMonth()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(view.nextMonth()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(view.currentMonth()).isTrue();

        CalendarView other = CalendarView.of(List.of(), YearMonth.of(2026, 12), LocalDate.of(2026, 9, 1));
        assertThat(other.currentMonth()).isFalse();
    }

    private CalendarDay dayOf(CalendarView view, LocalDate date) {
        return view.weeks().stream()
                .flatMap(List::stream)
                .filter(d -> d.date().equals(date))
                .findFirst().orElseThrow();
    }

    @Test
    void dayShowsThreeEventsDeadlinesFirstAndFoldsTheRest() {
        LocalDate d = LocalDate.of(2026, 10, 12);
        List<Announcement> many = new java.util.ArrayList<>();
        for (long i = 1; i <= 3; i++) many.add(announcement(i, "시작" + i, d, d.plusDays(3)));     // START x3
        for (long i = 4; i <= 5; i++) many.add(announcement(i, "마감" + i, d.minusDays(3), d));   // END x2

        CalendarDay day = dayOf(CalendarView.of(many, YearMonth.of(2026, 10), d), d);

        assertThat(day.visibleEvents()).extracting(CalendarEvent::houseName)
                .containsExactly("마감4", "마감5", "시작1");
        assertThat(day.hiddenEvents()).extracting(CalendarEvent::houseName).containsExactly("시작2", "시작3");
    }

    @Test
    void agendaListsOnlyDaysWithEventsFromTodayInCurrentMonth() {
        LocalDate today = LocalDate.of(2026, 10, 12);
        List<Announcement> list = List.of(
                announcement(1L, "지난", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 14)),
                announcement(2L, "다음달", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 2)));

        CalendarView view = CalendarView.of(list, YearMonth.of(2026, 10), today);

        // 10-05(지난 시작)는 빼고, 10-14(마감)·10-20(시작)만. 11-02 는 다음 달이라 없음
        assertThat(view.agendaDays()).extracting(CalendarDay::date)
                .containsExactly(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 20));
        assertThat(view.agendaDays().get(0).agendaLabel()).isEqualTo("10.14 (수)");

        // 다른 달은 처음부터 다 보여 준다
        CalendarView nov = CalendarView.of(list, YearMonth.of(2026, 11), today);
        assertThat(nov.agendaDays()).extracting(CalendarDay::date).containsExactly(LocalDate.of(2026, 11, 2));
    }
}
