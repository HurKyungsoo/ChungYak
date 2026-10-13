package com.portfolio.chungyak.llm;

/**
 * 외부 LLM 의 요청 한도 초과(HTTP 429) — 무료 등급 분당·하루 토큰 한도(#32). 다른 실패와 구분해
 * 사용자에게 "질문을 이해 못 했다"가 아니라 "잠시 뒤 다시"라고 알리기 위해 따로 둔다(#25).
 */
public class LlmRateLimitedException extends RuntimeException {

    public LlmRateLimitedException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Groq(이 예외)·Anthropic SDK(RateLimitException) 어느 쪽이든 한도 초과인지 */
    public static boolean isRateLimit(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof LlmRateLimitedException) return true;
            String name = t.getClass().getSimpleName();
            if (name.contains("RateLimit") || name.contains("TooManyRequests")) return true;
        }
        return false;
    }
}
