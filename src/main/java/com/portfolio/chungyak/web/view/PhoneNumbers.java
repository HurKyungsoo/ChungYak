package com.portfolio.chungyak.web.view;

/**
 * 문의처 전화번호 표시 포맷.
 *
 * 청약홈 {@code MDHS_TELNO} 는 하이픈 없는 숫자로 온다(실측 2026-09-21, 2,875건):
 * 8자리 1,435건(대표번호 1522-0125), 10자리 1,325건(02-2015-1045 / 032-613-2355),
 * 9자리 76건(02-303-3457), 11자리 38건(031-8094-1260 / 010-2085-0222),
 * 이미 하이픈이 들어온 12자리 1건(080-783-3000).
 *
 * 규칙에 없는 모양은 <b>손대지 않고 그대로 돌려준다</b> — 잘못 끊어 읽히느니 원본이 낫다.
 */
public final class PhoneNumbers {

    private PhoneNumbers() {}

    public static String format(String raw) {
        if (raw == null || raw.isBlank()) return raw;

        String trimmed = raw.trim();
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.length() != trimmed.length()) {
            return trimmed;   // 이미 하이픈 등으로 정리된 값 — 그대로 둔다
        }

        return switch (digits.length()) {
            case 8 -> digits.substring(0, 4) + "-" + digits.substring(4);
            case 9 -> digits.substring(0, 2) + "-" + digits.substring(2, 5) + "-" + digits.substring(5);
            case 10 -> digits.startsWith("02")
                    ? digits.substring(0, 2) + "-" + digits.substring(2, 6) + "-" + digits.substring(6)
                    : digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
            case 11 -> digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
            default -> trimmed;
        };
    }
}
