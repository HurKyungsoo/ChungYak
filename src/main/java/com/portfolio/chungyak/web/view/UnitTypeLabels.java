package com.portfolio.chungyak.web.view;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 주택형·면적 표기 — 화면용.
 *
 * 청약홈 주택형은 "084.9730A"처럼 전용면적 소수 넷째 자리까지 온다. 사람들은 "84A 타입"이라 부르므로
 * 정수 부분 + 접미사로 줄여 보여 주고, 원래 값은 옆에 작게 둔다(공고문 표와 대조할 때 필요하다).
 * LH 는 이미 "26A"처럼 짧게 와서 그대로다. 정수 부분은 반올림하지 않는다 — 84.97 을 85 로 부르지 않는다.
 */
public final class UnitTypeLabels {

    private static final Pattern TYPE = Pattern.compile("^0*(\\d+)(?:\\.\\d+)?\\s*([A-Za-z가-힣]*)$");

    private UnitTypeLabels() {}

    /** "084.9730A" → "84A", "059.9" → "59". 형식이 다르면 그대로. */
    public static String shortName(String typeName) {
        if (typeName == null) return null;
        Matcher m = TYPE.matcher(typeName.trim());
        if (!m.matches()) return typeName;
        String head = m.group(1).isEmpty() ? "0" : m.group(1);
        return head + m.group(2);
    }

    /** 줄인 이름이 원래 값과 다를 때만 원래 값을 따로 보여 준다 */
    public static boolean shortened(String typeName) {
        return typeName != null && !typeName.equals(shortName(typeName));
    }

    /** 면적 문자열 "109.5711" → "109.57", "38.50" → "38.5". 숫자가 아니면 그대로. */
    public static String area(String supplyArea) {
        if (supplyArea == null || supplyArea.isBlank()) return null;
        try {
            return new BigDecimal(supplyArea.trim()).setScale(2, RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return supplyArea;
        }
    }
}
