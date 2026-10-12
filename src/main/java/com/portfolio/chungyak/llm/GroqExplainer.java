package com.portfolio.chungyak.llm;

/**
 * {@link LlmExplainer} 의 Groq 구현 — 프롬프트·검증은 Anthropic 구현과 같다({@link AnthropicExplainer}).
 * 재구성만 하고, 어긋나면 {@link ContradictionCheck} 가 걸러 {@link FallbackSummary} 로 대체한다.
 * {@code llm.groq.explain-enabled=true} 일 때만 빈이 만들어진다(GroqProperties 참고).
 */
class GroqExplainer implements LlmExplainer {

    private static final int MAX_TOKENS = 1024;

    private final GroqChatClient client;

    GroqExplainer(GroqChatClient client) {
        this.client = client;
    }

    @Override
    public String explain(String facts) {
        return client.chat(AnthropicExplainer.SYSTEM_PROMPT, facts, MAX_TOKENS, false);
    }
}
