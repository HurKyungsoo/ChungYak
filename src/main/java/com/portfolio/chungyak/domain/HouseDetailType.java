package com.portfolio.chungyak.domain;

/**
 * 주택상세구분코드 (청약홈 HOUSE_DTL_SECD)
 *
 * 국민주택(03)이면서 PUBLIC_HOUSE_SPCLW_APPLC_AT='Y' 인 경우가 공공주택이고,
 * 이때만 청년·신생아 특별공급 세대수가 채워진다. (Swagger Mdl 설명 참고)
 */
public enum HouseDetailType {
    PRIVATE("01", "민영"),
    PUBLIC("03", "국민"),
    UNKNOWN("", "미상");

    private final String code;
    private final String label;

    HouseDetailType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static HouseDetailType from(String code) {
        if (code == null) return UNKNOWN;
        for (HouseDetailType t : values()) {
            if (t.code.equals(code)) return t;
        }
        return UNKNOWN;
    }
}
