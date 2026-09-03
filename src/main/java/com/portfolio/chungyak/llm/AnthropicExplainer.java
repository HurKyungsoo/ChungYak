package com.portfolio.chungyak.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;

/**
 * {@link LlmExplainer} 의 Anthropic 구현.
 *
 * 여기서 LLM 은 <b>재구성만</b> 한다 — 판정은 이미 규칙 엔진이 끝냈고,
 * 그 근거(satisfied/failed/missing)를 읽기 좋은 문장으로 풀어쓸 뿐이다.
 * 시스템 프롬프트로 "새 판정·숫자·추천 금지" 를 명시한다.
 * 그래도 지어낼 수 있으므로 {@link ContradictionCheck} 가 사후 검사한다.
 */
class AnthropicExplainer implements LlmExplainer {

    private static final long MAX_TOKENS = 1024L;

    private static final String SYSTEM_PROMPT = """
            너는 청약 자격 판정 결과를 설명하는 도우미다.
            아래 판정은 규칙 엔진이 이미 확정한 것이다. 너의 일은 주어진 근거를
            자연스러운 한국어 2~4문장으로 정리하는 것뿐이다.

            반드시 지킬 것:
            - 판정 결과나 세대수를 새로 만들어내지 마라. 주어진 근거에 없는 내용을
              덧붙이지 마라.
            - "신청 가능/불가능"은 주어진 판정과 정확히 일치해야 한다.
              신청 가능한 유형이 없다고 돼 있으면 "없습니다"라고 써라.
            - 새로운 조건·추천·다음 단계를 지어내지 마라.
            - 근거를 그대로 나열하지 말고 읽기 쉽게 풀어써라.
            - 판정 근거 목록은 화면에 그대로 표시되니, 요약에서 반복하지 않아도 된다.
            """;

    private final AnthropicClient client;
    private final String model;

    AnthropicExplainer(AnthropicClient client, LlmProperties properties) {
        this.client = client;
        this.model = properties.model();
    }

    @Override
    public String explain(String facts) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM_PROMPT)
                .addUserMessage(facts)
                .build();

        StringBuilder out = new StringBuilder();
        client.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .forEach(text -> out.append(text.text()));
        return out.toString().strip();
    }
}
