package com.portfolio.chungyak.llm;

import com.portfolio.chungyak.rag.DocumentAnswerer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** 어떤 키 조합에서 무엇이 켜지는지 — 특히 "소득·자산이 든 판정 요약은 기본으로 안 켜진다". */
class GroqClientConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GroqClientConfig.class);

    @Test
    @DisplayName("키가 없으면 아무것도 켜지지 않는다 (앱은 정상 기동)")
    void noKeyNothing() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(GroqChatClient.class)
                .doesNotHaveBean(LlmProfileCaller.class));
    }

    @Test
    @DisplayName("Groq 키만 있으면 자연어 추출·Q&A 답변이 켜지고, 판정 요약은 꺼진 채다")
    void groqKeyEnablesExtractionButNotExplainer() {
        runner.withPropertyValues("llm.groq.api-key=gsk_test").run(ctx -> {
            assertThat(ctx).hasSingleBean(LlmProfileCaller.class).hasSingleBean(DocumentAnswerer.class);
            assertThat(ctx).doesNotHaveBean(LlmExplainer.class);
        });
    }

    @Test
    @DisplayName("explain-enabled=true 로 명시해야만 판정 요약도 외부로 나간다")
    void explainerOnlyWhenOptedIn() {
        runner.withPropertyValues("llm.groq.api-key=gsk_test", "llm.groq.explain-enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(LlmExplainer.class));
    }

    @Test
    @DisplayName("Anthropic 키가 있으면 Groq 는 꺼진다 — 둘 다 켜져 판정 근거가 두 곳으로 가지 않게")
    void anthropicWins() {
        runner.withPropertyValues("llm.groq.api-key=gsk_test", "llm.anthropic.api-key=sk-ant-test")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(GroqChatClient.class)
                        .doesNotHaveBean(LlmProfileCaller.class));
    }

    @Test
    @DisplayName("REPLACE_ME 자리표시자는 키로 보지 않는다")
    void placeholderIsNotAKey() {
        runner.withPropertyValues("llm.groq.api-key=REPLACE_ME")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(GroqChatClient.class));
    }
}
