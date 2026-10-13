package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.llm.GroqChatClient;

import java.util.List;

/**
 * {@link DocumentAnswerer} 의 Groq 구현 — 프롬프트는 Anthropic 구현과 같다(발췌문에만 근거, 판정 금지).
 * 입력은 공개 공고문 발췌와 질문뿐이라 소득·자산 같은 개인 판정 정보는 나가지 않는다.
 * 검색은 VOYAGE_API_KEY 가 없으면 BM25(키워드)만으로 돈다(#22) — Groq 만으로 Q&A 가 켜진다.
 */
public class GroqDocumentAnswerer implements DocumentAnswerer {

    private static final int MAX_TOKENS = 1024;

    private final GroqChatClient client;

    public GroqDocumentAnswerer(GroqChatClient client) {
        this.client = client;
    }

    @Override
    public String answer(String question, List<String> numberedExcerpts) {
        String userMessage = "공고문 발췌:\n" + String.join("\n\n", numberedExcerpts) + "\n\n질문: " + question;
        return client.chat(AnthropicDocumentAnswerer.SYSTEM_PROMPT, userMessage, MAX_TOKENS, false);
    }
}
