package com.portfolio.chungyak.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 개선 안내는 두 수치의 <b>차이</b>만 계산한다 — 새 제도 수치를 만들지 않는다.
 */
class ImprovementHintsTest {

    @Test
    @DisplayName("청약통장 기간 — 요건과의 차이를 개월로 안내")
    void accountMonths() {
        assertThat(ImprovementHints.accountMonths(12, 24))
                .contains("12개월 더").contains("요건(24개월)");
        assertThat(ImprovementHints.accountMonths(3, 6)).contains("3개월 더");
    }

    @Test
    @DisplayName("거주 기간 — 차이 + 기타지역 신청 가능 안내")
    void residenceMonths() {
        String h = ImprovementHints.residenceMonths(23, 24, "투기과열지구·청약과열지역");
        assertThat(h).contains("1개월 더").contains("24개월").contains("기타지역");
    }

    @Test
    @DisplayName("재당첨 제한 — 남은 개월")
    void reWinMonths() {
        assertThat(ImprovementHints.reWinMonths(48, 60)).contains("12개월").contains("풀립니다");
    }

    @Test
    @DisplayName("예치금 — 부족분을 읽기 쉬운 금액으로")
    void deposit() {
        assertThat(ImprovementHints.deposit(2_000_000, 3_000_000))
                .contains("100만원").contains("300만원");
    }

    @Test
    @DisplayName("납입 횟수 — 부족 횟수")
    void paymentCount() {
        assertThat(ImprovementHints.paymentCount(6, 12)).contains("6회 더").contains("12회");
    }
}
