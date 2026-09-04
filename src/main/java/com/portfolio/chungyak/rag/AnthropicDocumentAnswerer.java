package com.portfolio.chungyak.rag;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;

import java.util.List;

/**
 * {@link DocumentAnswerer} 의 Anthropic 구현.
 *
 * 공고문 발췌문만 컨텍스트로 준다. 시스템 프롬프트로 "발췌에 없으면 지어내지 말고
 * 확인되지 않는다고 답하라"를 강제한다. 자격 판정에는 절대 관여하지 않는다 —
 * "이 조건이면 신청 가능한가요?" 같은 질문에도 공고문에 적힌 사실만 전달한다.
 */
class AnthropicDocumentAnswerer implements DocumentAnswerer {

    private static final long MAX_TOKENS = 1024L;

    private static final String SYSTEM_PROMPT = """
            너는 분양 공고문 내용을 안내하는 도우미다.
            아래에 번호가 붙은 공고문 발췌문이 주어진다. 사용자 질문에 이 발췌문만 근거로 답한다.

            반드시 지킬 것:
            - 발췌문에 있는 내용만 말한다. 발췌문에 근거가 없으면
              "공고문 발췌에서는 확인되지 않습니다. 원문을 확인하세요." 라고 답한다.
            - 근거가 된 발췌 번호를 문장 끝에 [1], [2] 처럼 표기한다.
            - 자격이 되는지/안 되는지 판정하지 않는다. 공고문에 적힌 요건·일정·절차를
              그대로 옮겨 전달만 한다. ("자격이 됩니다" 같은 단정 금지)
            - 한국어 2~5문장. 발췌문을 그대로 붙여넣지 말고 질문에 맞게 정리한다.
            """;

    private final AnthropicClient client;
    private final String model;

    AnthropicDocumentAnswerer(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public String answer(String question, List<String> numberedExcerpts) {
        String context = String.join("\n\n", numberedExcerpts);
        String userMessage = "공고문 발췌:\n" + context + "\n\n질문: " + question;

        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM_PROMPT)
                .addUserMessage(userMessage)
                .build();

        StringBuilder out = new StringBuilder();
        client.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .forEach(text -> out.append(text.text()));
        return out.toString().strip();
    }
}
