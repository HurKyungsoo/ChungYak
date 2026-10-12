package com.portfolio.chungyak.web.view;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 월 캘린더 그리드의 칸 하나. {@code inMonth=false} 는 앞/뒤 달이 채워 넣은 칸. */
public record CalendarDay(LocalDate date, boolean inMonth, boolean today, List<CalendarEvent> events) {

    /** 칸에 바로 보이는 일정 수 — 넘치면 "+N개 더"로 접는다 (하루 20건씩 몰리는 날이 있다). */
    static final int VISIBLE_LIMIT = 3;

    /** 마감 먼저 — 놓치면 끝나는 쪽이 위에 온다. 같은 종류 안에서는 들어온 순서 그대로. */
    public List<CalendarEvent> sortedEvents() {
        return events.stream()
                .sorted(Comparator.comparing(e -> e.kind() == CalendarEvent.Kind.END ? 0 : 1))
                .toList();
    }

    public List<CalendarEvent> visibleEvents() {
        List<CalendarEvent> sorted = sortedEvents();
        return sorted.subList(0, Math.min(VISIBLE_LIMIT, sorted.size()));
    }

    public List<CalendarEvent> hiddenEvents() {
        List<CalendarEvent> sorted = sortedEvents();
        return sorted.size() <= VISIBLE_LIMIT ? List.of() : sorted.subList(VISIBLE_LIMIT, sorted.size());
    }

    public List<CalendarEvent> endEvents() {
        return events.stream().filter(e -> e.kind() == CalendarEvent.Kind.END).toList();
    }

    public List<CalendarEvent> startEvents() {
        return events.stream().filter(e -> e.kind() == CalendarEvent.Kind.START).toList();
    }

    /** 모바일 일정 목록 머리 — "10.12 (월)" */
    public String agendaLabel() {
        return String.format("%d.%02d (%s)", date.getMonthValue(), date.getDayOfMonth(),
                date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN));
    }
}
