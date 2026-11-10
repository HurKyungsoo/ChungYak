package com.portfolio.chungyak.web.view;

/**
 * 주소 표시 — 목록 카드에 시·도 다음 단위(시·군·구)를 붙이려고.
 *
 * 청약홈 {@code HSSPLY_ADRES} 는 "서울특별시 서초구 반포동 1-1", "경기도 고양시 덕양구 …",
 * "전남광주통합특별시 북구 …"처럼 온다. 시·도 토큰(…도, …특별시·광역시·자치시)을 건너뛰고
 * 처음 나오는 …시·…군·…구 하나를 쓴다. 세종처럼 시·군·구가 없으면 {@code null} — 지어내지 않는다.
 */
public final class Addresses {

    private Addresses() {}

    public static String district(String address) {
        if (address == null || address.isBlank()) return null;
        String[] tokens = address.trim().split("\\s+");
        for (int i = 0; i < Math.min(tokens.length, 3); i++) {
            String t = tokens[i];
            if (t.endsWith("도") || t.contains("특별시") || t.contains("광역시") || t.contains("자치시")) continue;
            if (t.length() >= 2 && (t.endsWith("시") || t.endsWith("군") || t.endsWith("구"))) return t;
            return null;   // 시·도 다음이 시·군·구가 아니면(읍·동 등) 붙이지 않는다
        }
        return null;
    }
}
