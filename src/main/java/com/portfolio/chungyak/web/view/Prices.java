package com.portfolio.chungyak.web.view;

import java.text.DecimalFormat;

/**
 * 분양가 표시 포맷. {@code UnitType.topAmount} 는 <b>만원 단위</b>다
 * (청약홈 {@code LTTOT_TOP_AMOUNT} 가 만원 단위로 오고, 원 단위로 주는 LH 는
 * {@code LhClient} 가 만원으로 맞춰 넣는다).
 *
 * 실측 2026-09-23 기준 주택형 14,713건 전부 값이 차 있고 범위는 116 ~ 1,600,000(만원)이다.
 * 89300 -> "8억 9,300만원", 50000 -> "5억원", 5000 -> "5,000만원".
 */
public final class Prices {

    private static final DecimalFormat COMMA = new DecimalFormat("#,###");

    private Prices() {}

    /** 값이 없으면 {@code null} — 화면에서 "-" 로 대신한다. 지어내지 않는다. */
    public static String formatManwon(Integer manwon) {
        if (manwon == null || manwon <= 0) return null;

        int eok = manwon / 10_000;
        int man = manwon % 10_000;
        if (eok == 0) return COMMA.format(man) + "만원";
        if (man == 0) return COMMA.format(eok) + "억원";
        return COMMA.format(eok) + "억 " + COMMA.format(man) + "만원";
    }
}
