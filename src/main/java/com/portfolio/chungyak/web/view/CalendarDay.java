package com.portfolio.chungyak.web.view;

import java.time.LocalDate;
import java.util.List;

/** 월 캘린더 그리드의 칸 하나. {@code inMonth=false} 는 앞/뒤 달이 채워 넣은 칸. */
public record CalendarDay(LocalDate date, boolean inMonth, boolean today, List<CalendarEvent> events) {
}
