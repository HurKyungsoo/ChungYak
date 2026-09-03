package com.portfolio.chungyak.llm;

import java.util.List;

/**
 * 자연어 -> ApplicantProfile 추출 결과.
 *
 * 판정 결과가 아니다. 이건 "폼을 어떻게 채워줄까"에 대한 답이고,
 * 실제 판정은 사용자가 이 값을 확인·수정해 폼을 제출한 뒤 EligibilityEngine 이 한다.
 */
public record ProfileExtractionResult(
        Status status,
        ExtractedProfile profile,          // DISABLED / FAILED 이면 null
        List<String> unknownFieldLabels,   // LLM 이 채우지 못한 항목 (사용자에게 되물음)
        String message) {                  // 사용자에게 보여줄 안내 문구

    public enum Status {
        /** 추출 성공 (일부 필드는 null 일 수 있음) */
        EXTRACTED,
        /** ANTHROPIC_API_KEY 미설정 — 기능 비활성화 */
        DISABLED,
        /** LLM 호출/파싱 실패 — 사용자는 폼을 직접 채우면 된다 */
        FAILED
    }

    public static ProfileExtractionResult disabled() {
        return new ProfileExtractionResult(Status.DISABLED, null, List.of(),
                "자연어 입력 기능이 꺼져 있습니다. 아래 폼에 직접 입력하세요.");
    }

    public static ProfileExtractionResult failed(String reason) {
        return new ProfileExtractionResult(Status.FAILED, null, List.of(),
                "질문을 이해하지 못했습니다(" + reason + "). 아래 폼에 직접 입력하세요.");
    }

    public static ProfileExtractionResult extracted(ExtractedProfile profile) {
        List<String> unknown = profile.unknownFieldLabels();
        String msg = unknown.isEmpty()
                ? "답변에서 조건을 모두 읽었습니다. 아래 값이 맞는지 확인하고 판정하세요."
                : "일부 항목은 답변에서 확인되지 않았습니다. 아래에서 직접 선택한 뒤 판정하세요.";
        return new ProfileExtractionResult(Status.EXTRACTED, profile, unknown, msg);
    }

    public boolean isExtracted() {
        return status == Status.EXTRACTED;
    }
}
