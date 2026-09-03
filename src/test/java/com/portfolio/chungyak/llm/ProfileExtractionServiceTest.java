package com.portfolio.chungyak.llm;

import com.portfolio.chungyak.llm.ProfileExtractionResult.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProfileExtractionService 단위 검증.
 *
 * 실제 LLM 은 부르지 않는다 — {@link LlmProfileCaller} 를 목으로 고정하고,
 * 서비스가 그 응답(특히 null 필드)을 어떻게 다루는지만 본다.
 * 라이브 호출 검증은 {@link ProfileExtractionIntegrationTest} 에 분리돼 있다.
 */
class ProfileExtractionServiceTest {

    private static ProfileExtractionService withCaller(LlmProfileCaller caller) {
        return new ProfileExtractionService(Optional.of(caller));
    }

    @Test
    @DisplayName("API 키 없음(caller 없음) → DISABLED, LLM 호출 안 함")
    void disabledWhenNoCaller() {
        ProfileExtractionService service = new ProfileExtractionService(Optional.empty());

        ProfileExtractionResult result = service.extract("결혼한 지 3년 됐어요");

        assertThat(service.isAvailable()).isFalse();
        assertThat(result.status()).isEqualTo(Status.DISABLED);
        assertThat(result.profile()).isNull();
    }

    @Test
    @DisplayName("빈 입력 → LLM 호출 없이 FAILED")
    void blankInputFailsFast() {
        ProfileExtractionService service = withCaller(text -> {
            throw new AssertionError("빈 입력인데 LLM 을 불렀다");
        });

        assertThat(service.extract("   ").status()).isEqualTo(Status.FAILED);
    }

    @Test
    @DisplayName("애매한 입력 — LLM 이 married 만 채우고 나머지는 null → 그 null 이 그대로 미확인 필드가 된다")
    void ambiguousInputKeepsFieldsNull() {
        // "신혼인 것 같은데" 류: 혼인 여부만 true, 기간·나머지는 모름
        ExtractedProfile mocked = new ExtractedProfile(
                true, null, null, null, null, null, null, null, null);
        ProfileExtractionService service = withCaller(text -> mocked);

        ProfileExtractionResult result = service.extract("신혼인 것 같은데 잘 모르겠어요");

        assertThat(result.status()).isEqualTo(Status.EXTRACTED);
        assertThat(result.profile().married()).isTrue();
        assertThat(result.profile().monthsSinceMarriage()).isNull();
        // 미확인 필드가 그대로 사용자에게 되물어진다
        assertThat(result.unknownFieldLabels())
                .contains("혼인 기간(개월)", "무주택 여부", "청약통장 가입 기간(개월)")
                .doesNotContain("혼인 여부");
    }

    @Test
    @DisplayName("LLM 이 아무 것도 못 뽑음(전 필드 null) → FAILED")
    void allNullIsFailure() {
        ExtractedProfile empty = new ExtractedProfile(
                null, null, null, null, null, null, null, null, null);
        ProfileExtractionService service = withCaller(text -> empty);

        assertThat(service.extract("오늘 날씨 좋네요").status()).isEqualTo(Status.FAILED);
    }

    @Test
    @DisplayName("LLM 응답이 null → FAILED")
    void nullResponseIsFailure() {
        ProfileExtractionService service = withCaller(text -> null);

        assertThat(service.extract("결혼했고 아이 둘").status()).isEqualTo(Status.FAILED);
    }

    @Test
    @DisplayName("LLM 호출이 예외를 던지면 FAILED (되물음 없이 폼 직접 입력으로 넘어감)")
    void llmExceptionBecomesFailed() {
        ProfileExtractionService service = withCaller(text -> {
            throw new RuntimeException("429 rate limit");
        });

        ProfileExtractionResult result = service.extract("결혼한 지 3년 됐어요");

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(result.profile()).isNull();
    }

    @Test
    @DisplayName("전부 채워지면 미확인 필드가 비어 있다")
    void fullyExtractedHasNoUnknowns() {
        ExtractedProfile full = new ExtractedProfile(
                true, 40, 1, false, true, 24, false, false, true);
        ProfileExtractionService service = withCaller(text -> full);

        ProfileExtractionResult result = service.extract("...");

        assertThat(result.status()).isEqualTo(Status.EXTRACTED);
        assertThat(result.unknownFieldLabels()).isEmpty();
    }
}
