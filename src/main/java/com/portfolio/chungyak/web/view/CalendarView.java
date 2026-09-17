package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.domain.Announcement;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 월별 청약 캘린더 그리드 (6주 x 7일 = 42칸, 일요일 시작).
 *
 * 접수 시작일·마감일을 해당 날짜 칸에 배치한다. 넘겨받는 공고 목록은 이미
 * 접수중·접수예정만 걸러진 것(지난 공고는 저장소 조회 단계에서 빠짐)이라
 * 과거 달을 넘겨보면 빈 칸으로 보이는 게 정상이다 — 이 캘린더는 "앞으로의 일정" 용도다.
 */
public record CalendarView(YearMonth month, String label, List<List<CalendarDay>> weeks,
                            YearMonth prevMonth, YearMonth nextMonth, boolean currentMonth) {

    private static final int GRID_DAYS = 42;

    public static CalendarView of(List<Announcement> announcements, YearMonth month, LocalDate today) {
        LocalDate firstOfMonth = month.atDay(1);
        LocalDate gridStart = firstOfMonth.minusDays(firstOfMonth.getDayOfWeek().getValue() % 7);

        Map<LocalDate, List<CalendarEvent>> eventsByDate = new LinkedHashMap<>();
        for (Announcement a : announcements) {
            addEvent(eventsByDate, a.getReceptBeginDate(), a, CalendarEvent.Kind.START);
            addEvent(eventsByDate, a.getReceptEndDate(), a, CalendarEvent.Kind.END);
        }

        List<CalendarDay> days = new ArrayList<>(GRID_DAYS);
        for (int i = 0; i < GRID_DAYS; i++) {
            LocalDate date = gridStart.plusDays(i);
            days.add(new CalendarDay(date, YearMonth.from(date).equals(month), date.equals(today),
                    eventsByDate.getOrDefault(date, List.of())));
        }

        List<List<CalendarDay>> weeks = new ArrayList<>();
        for (int i = 0; i < GRID_DAYS; i += 7) {
            weeks.add(days.subList(i, i + 7));
        }

        return new CalendarView(month, month.getYear() + "년 " + month.getMonthValue() + "월", weeks,
                month.minusMonths(1), month.plusMonths(1), month.equals(YearMonth.from(today)));
    }

    private static void addEvent(Map<LocalDate, List<CalendarEvent>> byDate, LocalDate date,
                                  Announcement a, CalendarEvent.Kind kind) {
        if (date == null) return;
        byDate.computeIfAbsent(date, d -> new ArrayList<>())
                .add(new CalendarEvent(a.getId(), a.getHouseName(), kind));
    }
}
