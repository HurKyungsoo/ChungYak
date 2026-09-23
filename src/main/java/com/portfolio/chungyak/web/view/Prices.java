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

    /** 1평 = 400/121 ㎡. 평당가는 관례대로 전용면적이 아니라 공급면적으로 나눈다. */
    private static final double M2_PER_PYEONG = 400d / 121d;

    private Prices() {}

    /**
     * 공급면적 기준 평당가(만원/평). 면적은 "109.4560" 같은 문자열로 들어온다 —
     * 없거나 숫자로 읽히지 않으면 {@code null} 이다(지어내지 않는다).
     */
    public static Integer perPyeong(Integer manwon, String supplyAreaM2) {
        if (manwon == null || manwon <= 0 || supplyAreaM2 == null || supplyAreaM2.isBlank()) return null;

        double area;
        try {
            area = Double.parseDouble(supplyAreaM2.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (area <= 0) return null;

        return (int) Math.round(manwon / (area / M2_PER_PYEONG));
    }

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
