package com.portfolio.chungyak.domain;

/**
 * 공고 구분 — 목록 탭·배지용. 판정에는 쓰지 않는다.
 *
 * 청약홈은 APT 분양정보 API 만 수집하므로 분양(또는 신혼희망타운)이다.
 * LH 는 목록 응답의 상위유형코드(UPP_AIS_TP_CD)로 가른다 (2026-10-12 라이브 확인):
 *  05 분양주택 → SALE / 06 임대주택·13 주거복지(매입임대) → RENTAL / 39 공공분양(신혼희망) → HOPE_TOWN.
 *
 * ★ 39 아래에는 "행복주택(신혼희망)"(AIS_TP_CD=42, 임대)도 있다 — 신혼희망타운이라고 다 분양이 아니다.
 * 그래서 세부 유형명(AIS_TP_CD_NM)을 {@code Announcement.supplyTypeName} 에 따로 보관해 함께 보여 준다.
 * "예비입주자(대기자) 모집"은 구분과 겹치므로(임대·신혼희망 모두) 여기 넣지 않고 제목으로 판별한다.
 */
public enum SupplyCategory {
    SALE("분양"),
    RENTAL("임대"),
    HOPE_TOWN("신혼희망타운");

    private final String label;

    SupplyCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** LH 목록 응답의 UPP_AIS_TP_CD → 구분. 주택이 아닌 코드면 null. */
    public static SupplyCategory fromLhUpperCode(String uppAisTpCd) {
        if (uppAisTpCd == null) return null;
        return switch (uppAisTpCd) {
            case "05" -> SALE;
            case "06", "13" -> RENTAL;
            case "39" -> HOPE_TOWN;
            default -> null;
        };
    }
}
