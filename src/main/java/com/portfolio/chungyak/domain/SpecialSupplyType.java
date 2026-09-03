package com.portfolio.chungyak.domain;

/**
 * 특별공급 유형.
 *
 * 이 프로젝트의 판정 대상. 청약홈 Mdl 응답의 세대수 필드와 1:1 대응한다.
 * 필드명이 API 마다 미묘하게 달라서(예: YGMN_HSHLDCO vs SPSPLY_YGMN_HSHLDCO)
 * 어댑터에서 이 enum 으로 정규화한 뒤 도메인은 이것만 안다.
 */
public enum SpecialSupplyType {
    MULTI_CHILD("다자녀가구"),
    NEWLYWED("신혼부부"),
    FIRST_TIME("생애최초"),
    OLD_PARENTS("노부모부양"),
    INSTITUTION_RECOMMEND("기관추천"),
    YOUTH("청년"),
    NEWBORN("신생아"),
    TRANSFER_INSTITUTION("이전기관"),
    ETC("기타");

    private final String label;

    SpecialSupplyType(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
