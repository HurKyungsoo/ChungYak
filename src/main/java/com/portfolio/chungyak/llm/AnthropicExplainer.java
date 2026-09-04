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
            자연스러운 한국어 2~5문장으로 정리하는 것뿐이다.

            반드시 지킬 것:
            - 판정 결과나 세대수를 새로 만들어내지 마라. 주어진 근거에 없는 내용을
              덧붙이지 마라.
            - "신청 가능/불가능"은 주어진 판정과 정확히 일치해야 한다.
              신청 가능한 유형이 없다고 돼 있으면 "없습니다"라고 써라.
            - 근거를 그대로 나열하지 말고 읽기 쉽게 풀어써라.
            - 판정 근거 목록은 화면에 그대로 표시되니, 요약에서 반복하지 않아도 된다.

            미충족 항목의 개선 경로:
            - '개선:' 으로 시작하는 근거가 있으면, 그건 규칙 엔진이 수치 차이로 미리
              계산해 둔 안내다. 그 내용을 자연스러운 문장으로 녹여서 전달하라.
              (예: "개선: 청약통장을 12개월 더 유지하면..." → "청약통장을 12개월 더
              유지하면 이 요건을 채울 수 있습니다")
            - '개선:' 근거가 없는 미충족 항목은, 근거 문장에 있는 두 수치로 "얼마나
              모자라는지" 정도만 말하고, 무엇을 하면 되는지는 지어내지 마라.
            - 근거에 없는 새 수치·기한·금액·조건을 만들어내지 마라.
            - 이 설명이 판정을 바꾸지는 않는다 — 자격 없음은 여전히 자격 없음이다.
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
