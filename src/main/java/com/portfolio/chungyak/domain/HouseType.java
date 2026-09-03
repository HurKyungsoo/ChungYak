package com.portfolio.chungyak.domain;

/**
 * 주택구분코드 (청약홈 HOUSE_SECD)
 * Swagger 명세에 값이 정의돼 있어 추측 없이 그대로 매핑한다.
 */
public enum HouseType {
    APT("01", "APT"),
    PRE_SUBSCRIPTION("09", "민간사전청약"),
    NEWLYWED_HOPE_TOWN("10", "신혼희망타운"),
    UNKNOWN("", "미상");

    private final String code;
    private final String label;

    HouseType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static HouseType from(String code) {
        if (code == null) return UNKNOWN;
        for (HouseType t : values()) {
            if (t.code.equals(code)) return t;
        }
        return UNKNOWN;
    }
}
