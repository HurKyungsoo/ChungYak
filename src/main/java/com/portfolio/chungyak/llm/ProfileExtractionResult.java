package com.portfolio.chungyak.llm;

import java.util.List;

/**
 * 자연어 -> ApplicantProfile 추출 결과.
 *
 * 판정 결과가 아니다. 이건 "폼을 어떻게 채워줄까"에 대한 답이고,
 * 실제 판정은 사용자가 이 값을 확인·수정해 폼을 제출한 뒤 EligibilityEngine 이 한다.
 *
 * 사용자가 값을 눈으로 검증하도록 세 가지를 더 싣는다({@link ExtractionReadBack}):
 * 어떻게 읽었는지(readBack), 어느 칸을 채웠는지(filledFields), 문장 속 숫자와 어긋나는 기간 경고(warnings).
 */
public record ProfileExtractionResult(
        Status status,
        ExtractedProfile profile,          // DISABLED / FAILED 이면 null
        List<String> unknownFieldLabels,   // LLM 이 채우지 못한 항목 (사용자에게 되물음)
        String message,                    // 사용자에게 보여줄 안내 문구
        List<String> readBack,             // "혼인 기간 36개월(3년)" — 채워진 값을 사람이 읽는 말로
        List<String> filledFields,         // AI 가 채운 폼 입력 이름 (칸에 "AI 입력" 표시)
        List<String> warnings) {           // 문장 속 기간 숫자와 안 맞을 때 — 값은 그대로 두고 확인만 요청

    public enum Status {
        /** 추출 성공 (일부 필드는 null 일 수 있음) */
        EXTRACTED,
        /** LLM 키(ANTHROPIC_API_KEY / GROQ_API_KEY) 미설정 — 기능 비활성화 */
        DISABLED,
        /** LLM 호출/파싱 실패 — 사용자는 폼을 직접 채우면 된다 */
        FAILED
    }

    public static ProfileExtractionResult disabled() {
        return new ProfileExtractionResult(Status.DISABLED, null, List.of(),
                "자연어 입력 기능이 꺼져 있습니다. 아래 폼에 직접 입력하세요.", List.of(), List.of(), List.of());
    }

    public static ProfileExtractionResult failed(String reason) {
        return new ProfileExtractionResult(Status.FAILED, null, List.of(),
                "질문을 이해하지 못했습니다(" + reason + "). 아래 폼에 직접 입력하세요.", List.of(), List.of(), List.of());
    }

    public static ProfileExtractionResult extracted(ExtractedProfile profile) {
        return extracted(profile, null);
    }

    /** @param naturalText 사용자가 쓴 원문 — 기간 숫자 대조용. null 이면 대조 경고를 만들지 않는다. */
    public static ProfileExtractionResult extracted(ExtractedProfile profile, String naturalText) {
        List<String> unknown = profile.unknownFieldLabels();
        String msg = unknown.isEmpty()
                ? "답변에서 조건을 모두 읽었습니다. 아래 값이 맞는지 확인하고 판정하세요."
                : "일부 항목은 답변에서 확인되지 않았습니다. 아래에서 직접 선택한 뒤 판정하세요.";
        return new ProfileExtractionResult(Status.EXTRACTED, profile, unknown, msg,
                ExtractionReadBack.readBack(profile),
                ExtractionReadBack.filledFields(profile),
                naturalText == null ? List.of() : ExtractionReadBack.warnings(naturalText, profile));
    }

    public boolean isExtracted() {
        return status == Status.EXTRACTED;
    }
}
