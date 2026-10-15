package com.portfolio.chungyak.rule;

/**
 * 과거 특별공급 당첨이 <b>누구의·언제</b> 것인지 — 주택공급규칙 제55조의3(2025. 3. 31. 개정) 특례를 가른다.
 *
 * 모르면 {@link #OTHER} — 특례 없이 "세대당 한 차례"(제55조)를 그대로 적용한다(예전 판정과 같다).
 */
public enum SpecialSupplyWinTiming {
    /** 혼인 후 당첨, 세대원 당첨, 또는 모름 — 특례 없음 */
    OTHER("혼인 후 또는 그 밖"),
    /** 본인이 혼인신고 전에 당첨 — 신혼부부 특공은 한 차례 더(제55조의3제2항) */
    SELF_BEFORE_MARRIAGE("내가 혼인신고 전에"),
    /** 배우자가 혼인신고 전에 당첨 — 신생아·신혼부부·생애최초 특공에서는 따지지 않는다(제55조의3제1항) */
    SPOUSE_BEFORE_MARRIAGE("배우자가 혼인신고 전에");

    private final String label;

    SpecialSupplyWinTiming(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
