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

    /**
     * 특별공급 유형 하나에 대한 판정 + 근거.
     *
     * satisfied/failed/missing/improvementHints 는 이 유형의 판정 이유를 하나도 빠짐없이 담은
     * 전체 목록이고(감사용, "전체 판정 근거" 섹션), commonChecks 는 그중 여러 유형이 공유하는
     * 요건(재당첨·소득·자산·통장·거주)만 뽑아 화면 상단 요약 레일에 쓴다. own* 은 commonChecks 에
     * 이미 나온 문장을 뺀 "이 유형만의" 이유라 요약 레일 아래 유형칩에 쓴다 — 같은 이유를
     * 화면에 두 번 강조하지 않기 위한 것뿐, 새 판정을 하는 게 아니다.
     */
    public record TypeDecision(
            String typeLabel,
            boolean eligible,
            boolean undetermined,
            List<String> satisfiedReasons,
            List<String> failedReasons,
            List<String> missingInputs,
            List<String> improvementHints,
            List<CommonCheckView> commonChecks,
            List<String> ownSatisfiedReasons,
            List<String> ownFailedReasons,
            List<String> ownMissingInputs,
            List<String> ownImprovementHints) {}
}
