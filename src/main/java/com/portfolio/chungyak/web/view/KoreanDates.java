package com.portfolio.chungyak.web.view;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * 화면용 짧은 날짜 — "10.14 (수)". 서버 로캘과 무관하게 요일을 한국어로 고정한다
 * (#temporals 의 요일 이름은 서버 로캘을 따라 "Wed" 로 나올 수 있다).
 */
public final class KoreanDates {

    private KoreanDates() {}

    public static String monthDay(LocalDate d) {
        if (d == null) return null;
        return String.format("%02d.%02d (%s)", d.getMonthValue(), d.getDayOfMonth(),
                d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN));
    }

    /** 요일 없이 "10.01" */
    public static String monthDayPlain(LocalDate d) {
        if (d == null) return null;
        return String.format("%02d.%02d", d.getMonthValue(), d.getDayOfMonth());
    }
}
