package com.portfolio.chungyak.web.view;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 카드/헤더 우측에 붙는 D-day 표시값.
 *
 * 접수예정이면 접수 시작일까지, 접수중이면 마감일까지 남은 일수를 센다.
 * 계산할 수 없거나(날짜 없음) 이미 지난 경우 {@code label} 은 null — 화면에서 표시하지 않는다.
 */
public record Dday(String label, boolean urgent) {

    private static final Dday NONE = new Dday(null, false);

    public static Dday of(String status, LocalDate begin, LocalDate end, LocalDate today) {
        LocalDate target = switch (status == null ? "" : status) {
            case "접수예정" -> begin;
            case "접수중" -> end;
            default -> null;
        };
        if (target == null || today == null) return NONE;
        long days = ChronoUnit.DAYS.between(today, target);
        if (days < 0) return NONE;
        String label = days == 0 ? "D-DAY" : "D-" + days;
        return new Dday(label, days <= 3);
    }

    public boolean present() {
        return label != null;
    }
}
