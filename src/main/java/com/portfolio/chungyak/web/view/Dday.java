package com.portfolio.chungyak.web.view;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 카드/헤더에 붙는 D-day 표시값.
 *
 * 접수예정이면 접수 시작일까지, 접수중이면 마감일까지 남은 일수를 센다. "D-2" 만 쓰면 무엇까지
 * 남았는지 모르니 "마감 D-2"·"접수 시작 D-5"처럼 기준을 붙인다 (#19-2).
 * 시작일을 몰라 상태를 못 정한 공고(LH 일부)라도 마감일이 아직 안 지났으면 "마감 D-n"은 말할 수 있다.
 * 계산할 수 없거나(날짜 없음) 이미 지난 경우 {@code label} 은 null — 화면에서 표시하지 않는다.
 */
public record Dday(String label, boolean urgent) {

    private static final Dday NONE = new Dday(null, false);

    public static Dday of(String status, LocalDate begin, LocalDate end, LocalDate today) {
        if (today == null) return NONE;
        String s = status == null ? "" : status;
        if ("접수예정".equals(s)) return label(begin, today, "오늘 접수 시작", "접수 시작 D-");
        // 시작일을 몰라 상태가 "-" 인 공고만 마감 D-day 를 센다 — 취소·정정 전 공고(#47)엔 세지 않는다
        if ("접수중".equals(s) || (begin == null && ("-".equals(s) || s.isEmpty()))) {
            return label(end, today, "오늘 마감", "마감 D-");
        }
        return NONE;
    }

    private static Dday label(LocalDate target, LocalDate today, String todayLabel, String prefix) {
        if (target == null) return NONE;
        long days = ChronoUnit.DAYS.between(today, target);
        if (days < 0) return NONE;
        return new Dday(days == 0 ? todayLabel : prefix + days, days <= 3);
    }

    public boolean present() {
        return label != null;
    }
}
