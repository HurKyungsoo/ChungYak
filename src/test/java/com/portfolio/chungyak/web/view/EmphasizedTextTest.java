package com.portfolio.chungyak.web.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmphasizedTextTest {

    @Test
    @DisplayName("내 값은 굵게, 괄호 안 기준값은 그대로")
    void boldsUserValueNotBasis() {
        assertThat(EmphasizedText.html("혼인기간이 100개월로 7년(84개월)을 초과했습니다."))
                .isEqualTo("혼인기간이 <strong>100개월</strong>로 <strong>7년</strong>(84개월)을 초과했습니다.");
        assertThat(EmphasizedText.html("청약통장 가입기간이 3개월로 요건(6개월)에 미달합니다."))
                .isEqualTo("청약통장 가입기간이 <strong>3개월</strong>로 요건(6개월)에 미달합니다.");
    }

    @Test
    @DisplayName("괄호 안의 기준 연도·퍼센트도 굵게 하지 않는다")
    void keepsParenthesizedBasis() {
        assertThat(EmphasizedText.html("월평균소득(2024년 기준) 대비 172%로 요건(160% 이하)을 초과합니다."))
                .isEqualTo("월평균소득(2024년 기준) 대비 <strong>172%</strong>로 요건(160% 이하)을 초과합니다.");
    }

    @Test
    @DisplayName("억·만원 복합 금액은 한 덩어리로")
    void compoundMoney() {
        assertThat(EmphasizedText.html("총자산 4억원이 요건(3억 7,900만원 이하)을 초과합니다 — 2,100만원 초과."))
                .isEqualTo("총자산 <strong>4억원</strong>이 요건(3억 7,900만원 이하)을 초과합니다 — <strong>2,100만원</strong> 초과.");
        assertThat(EmphasizedText.html("상한은 월 1,007만원으로 월 92만원 초과입니다."))
                .isEqualTo("상한은 월 <strong>1,007만원</strong>으로 월 <strong>92만원</strong> 초과입니다.");
    }

    @Test
    @DisplayName("HTML 은 이스케이프한다 — 이스케이프 결과의 숫자(&#39;)를 다시 굵게 하지 않는다")
    void escapesHtml() {
        assertThat(EmphasizedText.html("<script>alert(1)</script> 5개월 'x'"))
                .isEqualTo("&lt;script&gt;alert(1)&lt;/script&gt; <strong>5개월</strong> &#39;x&#39;");
    }

    @Test
    @DisplayName("null·숫자 없는 문장")
    void edgeCases() {
        assertThat(EmphasizedText.html(null)).isEmpty();
        assertThat(EmphasizedText.html("무주택 세대구성원 요건을 충족합니다.")).isEqualTo("무주택 세대구성원 요건을 충족합니다.");
    }
}
