package com.portfolio.chungyak.llm;

/**
 * 판정 결과 자연어 요약.
 *
 * 규칙 기반 이유 목록을 대체하는 게 아니다 — 그 위에 얹는 읽기용 한 문단이다.
 */
public record ExplanationResult(Status status, String text) {

    public enum Status {
        /** LLM 이 만들었고 모순 검사를 통과 — 화면에 "AI 요약" 으로 표시 */
        AI,
        /** LLM 비활성/실패/모순 → 규칙 데이터로 조립한 결정론적 요약 */
        FALLBACK,
        /** ANTHROPIC_API_KEY 없음 — 요약 영역을 아예 안 보여준다 */
        DISABLED
    }

    public static ExplanationResult ai(String text) {
        return new ExplanationResult(Status.AI, text);
    }

    public static ExplanationResult fallback(String text) {
        return new ExplanationResult(Status.FALLBACK, text);
    }

    public static ExplanationResult disabled() {
        return new ExplanationResult(Status.DISABLED, null);
    }

    /** 화면에 요약 영역을 그릴지 */
    public boolean isShown() {
        return status != Status.DISABLED;
    }

    /** "AI 요약" 배지를 붙일지 (FALLBACK 은 규칙이 만든 거라 AI 아님) */
    public boolean isAiGenerated() {
        return status == Status.AI;
    }
}
