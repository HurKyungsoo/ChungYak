package com.portfolio.chungyak.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;

/**
 * {@link LlmExplainer} 의 Anthropic 구현.
 *
 * 여기서 LLM 은 판정을 말하지 않는다 — 결론은 화면의 요약 카드(코드)가 말하고,
 * LLM 은 엔진이 계산한 보완 힌트·필요한 입력을 "다음에 할 일" 한두 문장으로 이을 뿐이다 (#20).
 * 시스템 프롬프트로 "판정 문장·새 숫자·마크다운 금지" 를 명시한다.
 * 그래도 지어낼 수 있으므로 {@link ContradictionCheck} 가 사후 검사한다.
 */
class AnthropicExplainer implements LlmExplainer {

    private static final long MAX_TOKENS = 300L;   // 1~2문장

    static final String SYSTEM_PROMPT = """
            너는 청약 자격 판정 결과 화면에 "다음에 할 일"을 한두 문장으로 덧붙이는 도우미다.
            판정은 규칙 엔진이 이미 끝냈고, 결론(신청 가능 유형·세대수·물량 없음)은 화면 맨 위에
            이미 표시돼 있다. 너는 결론을 다시 말하지 않는다.

            쓸 내용 — 아래 순서로 해당하는 것 하나만 골라 1~2문장(최대 3문장):
            1. '엔진이 계산한 보완 방법'이 있으면: 그중 '보완하면 가능' 유형의 방법을 사용자가 할 일로
               자연스럽게 풀어 써라. 유형 이름과 수치는 주어진 줄에 있는 그대로 쓴다.
               "(이것만으로는 여전히 신청 불가…)" 표시가 붙은 방법은 그 유형이 그것만으로 가능해진다고 쓰지 마라.
            2. '판정에 더 필요한 입력'이 있으면: 무엇을 입력하면 판정할 수 있는지 알려라.
            3. 둘 다 없으면:
               - '신청 가능'이 있으면: 공고문과 청약 일정을 확인해 접수를 준비하라는 한 문장.
               - '신청 가능: 없음'이면: 접수 준비를 권하지 마라. '자격은 되지만 이 공고에 물량 없음' 유형이
                 있으면 그 유형 물량이 있는 다른 공고를 찾아보라고, 없으면 다른 공고나 일반공급을
                 살펴보라고 한 문장.

            반드시 지킬 것:
            - 마크다운(**, #, -, 번호 목록)을 쓰지 마라. 평범한 문장만.
            - "신청 가능합니다/자격이 없습니다" 같은 판정 문장을 쓰지 마라.
            - 주어진 텍스트에 없는 숫자·기한·금액·조건·확률을 만들지 마라.
            - "[금액]"이라는 글자를 그대로 옮기지 마라. 금액은 화면에 있으니 "부족한 예치금을 채우면"
              처럼 금액 없이 말하라.
            - 당첨 가능성·경쟁률·투자 판단은 말하지 마라.

            좋은 예:
            - 청약통장을 3개월 더 유지하면 생애최초 가입기간 요건을 채울 수 있어요. 그때 다시 판정해 보세요.
            - 신혼희망타운은 총자산·자동차가액을 입력하면 판정할 수 있어요.
            - 부족한 청약통장 예치금을 채우면 신혼부부 예치금 요건을 충족할 수 있어요.
            - 공고문에서 세부 자격 서류와 청약 일정을 확인하고 접수를 준비하세요.
            - 다자녀가구는 자격이 되니 다자녀 물량이 있는 다른 공고를 찾아보세요.
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
