package com.portfolio.chungyak.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExtractedProfile 의 순수 로직 검증 (LLM 무관).
 * "애매하면 null, null 은 되물음" 규칙이 실제로 그렇게 동작하는지 본다.
 */
class ExtractedProfileTest {

    @Test
    @DisplayName("전 필드 null → 미확인 9개, 값 없음")
    void allNull() {
        ExtractedProfile p = new ExtractedProfile(null, null, null, null, null, null, null, null, null);

        assertThat(p.hasAnyValue()).isFalse();
        assertThat(p.unknownFieldLabels()).hasSize(9);
    }

    @Test
    @DisplayName("전 필드 채움 → 미확인 없음")
    void noneNull() {
        ExtractedProfile p = new ExtractedProfile(true, 40, 2, false, true, 24, false, true, true);

        assertThat(p.hasAnyValue()).isTrue();
        assertThat(p.unknownFieldLabels()).isEmpty();
    }

    @Test
    @DisplayName("일부만 채움 → 딱 그 null 필드만 되물음 목록에 남는다")
    void partial() {
        ExtractedProfile p = new ExtractedProfile(
                true, 36, null, null, true, null, null, null, null);

        assertThat(p.hasAnyValue()).isTrue();
        assertThat(p.unknownFieldLabels()).containsExactly(
                "미성년 자녀 수",
                "2세 이하 자녀(신생아) 유무",
                "청약통장 가입 기간(개월)",
                "과거 주택 소유 이력",
                "노부모 부양 여부",
                "세대주 여부");
    }
}
