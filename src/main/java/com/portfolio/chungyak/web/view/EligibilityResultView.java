package com.portfolio.chungyak.web.view;

import java.util.List;

/**
 * 자격 판정 결과 화면 모델.
 *
 * EligibilityEngine.MatchResult 를 화면이 그리기 쉬운 형태로 재배열한 것뿐이다 —
 * 판정은 이미 끝났고 여기서는 아무것도 다시 판단하지 않는다.
 *
 * 판정 이유(satisfied/failed/missing)는 하나도 빠뜨리지 않고 담는다.
 * 지금은 그대로 화면에 노출하고, 나중에 이 자리에 LLM 설명이 들어간다.
 */
public record EligibilityResultView(
        Long announcementId,
        String houseName,
        boolean regulatedArea,
        boolean hasAnyMatch,
        List<MatchedUnitType> matchedUnitTypes,
        List<TypeDecision> qualifiedButUnavailable,
        List<TypeDecision> allDecisions) {

    /**
     * 신청 가능한 주택형 하나 — 어떤 유형으로, 몇 세대 배정인지.
     * allocationCountKnown=false 면 세대수는 미상이고 totalAllocated·allocatedCount 는 0 이다(LH).
     */
    public record MatchedUnitType(
            String typeName,
            String supplyArea,
            boolean allocationCountKnown,
            int totalAllocated,
            List<AllocatedType> applicableTypes) {}

    public record AllocatedType(String typeLabel, int allocatedCount) {}

    /** 특별공급 유형 하나에 대한 판정 + 근거 */
    public record TypeDecision(
            String typeLabel,
            boolean eligible,
            boolean undetermined,
            List<String> satisfiedReasons,
            List<String> failedReasons,
            List<String> missingInputs,
            List<String> improvementHints) {}
}
