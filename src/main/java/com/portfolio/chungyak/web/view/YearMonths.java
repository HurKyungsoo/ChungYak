package com.portfolio.chungyak.web.view;

/**
 * 입주예정월 표시 포맷. 원본({@code MVN_PREARNGE_YM})은 "202910" 같은 YYYYMM 문자열이라
 * 그대로 찍으면 다른 날짜 필드(YYYY-MM-DD)와 모양이 어긋난다.
 *
 * {@link PhoneNumbers} 와 같은 원칙으로, 규칙에 없는 모양은 손대지 않고 그대로 돌려준다.
 */
public final class YearMonths {

    private YearMonths() {}

    public static String format(String raw) {
        if (raw == null || !raw.matches("\\d{6}")) return raw;
        return raw.substring(0, 4) + "-" + raw.substring(4);
    }
}
