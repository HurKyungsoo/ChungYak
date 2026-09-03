package com.portfolio.chungyak.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 일반공급 청약가점 계산 검증.
 *
 * 가점은 순수 산수다 — 경계값(무주택 12개월, 통장 6/12개월, 부양가족 6명)에서
 * 정확히 갈리고, 같은 입력이면 항상 같은 점수여야 한다.
 */
class GeneralSupplyScoreCalculatorTest {

    private final GeneralSupplyScoreCalculator calc = new GeneralSupplyScoreCalculator();

    private static GeneralSupplyInput input(Integer age, boolean married,
                                            Integer houselessMonths, Integer dependents,
                                            Integer accountMonths) {
        return new GeneralSupplyInput(age, married, houselessMonths, dependents, accountMonths);
    }

    // ── 무주택 기간 (32점) ────────────────────────────────────────────
    @Test
    @DisplayName("무주택 11개월은 2점, 12개월은 4점 (1년 경계)")
    void houselessOneYearBoundary() {
        assertThat(calc.calculate(input(40, true, 11, 0, 12)).houselessPeriod().score()).isEqualTo(2);
        assertThat(calc.calculate(input(40, true, 12, 0, 12)).houselessPeriod().score()).isEqualTo(4);
    }

    @Test
    @DisplayName("무주택 15년(180개월) 이상은 32점 상한")
    void houselessCap() {
        assertThat(calc.calculate(input(50, true, 180, 0, 12)).houselessPeriod().score()).isEqualTo(32);
        assertThat(calc.calculate(input(50, true, 600, 0, 12)).houselessPeriod().score()).isEqualTo(32);
        assertThat(calc.calculate(input(50, true, 168, 0, 12)).houselessPeriod().score()).isEqualTo(30); // 14년
    }

    @Test
    @DisplayName("만 30세 미만 미혼은 무주택 기간이 0점")
    void under30SingleIsZero() {
        GeneralSupplyScore.ScoreItem item =
                calc.calculate(input(28, false, 200, 2, 60)).houselessPeriod();
        assertThat(item.score()).isEqualTo(0);
        assertThat(item.detail()).contains("만 30세 미만 미혼");
    }

    @Test
    @DisplayName("만 30세 미만이라도 혼인 중이면 무주택 기간을 정상 산정")
    void under30MarriedIsCounted() {
        assertThat(calc.calculate(input(28, true, 36, 2, 60)).houselessPeriod().score()).isEqualTo(8);
    }

    // ── 부양가족 수 (35점) ────────────────────────────────────────────
    @Test
    @DisplayName("부양가족 0명 5점, 1명당 5점, 6명 이상 35점")
    void dependentsTable() {
        assertThat(calc.calculate(input(40, true, 12, 0, 12)).dependents().score()).isEqualTo(5);
        assertThat(calc.calculate(input(40, true, 12, 3, 12)).dependents().score()).isEqualTo(20);
        assertThat(calc.calculate(input(40, true, 12, 6, 12)).dependents().score()).isEqualTo(35);
        assertThat(calc.calculate(input(40, true, 12, 10, 12)).dependents().score()).isEqualTo(35);
    }

    // ── 청약통장 가입 기간 (17점) ─────────────────────────────────────
    @Test
    @DisplayName("통장 5개월 1점, 6개월 2점, 12개월 3점 (경계)")
    void accountBoundaries() {
        assertThat(calc.calculate(input(40, true, 12, 0, 5)).accountPeriod().score()).isEqualTo(1);
        assertThat(calc.calculate(input(40, true, 12, 0, 6)).accountPeriod().score()).isEqualTo(2);
        assertThat(calc.calculate(input(40, true, 12, 0, 11)).accountPeriod().score()).isEqualTo(2);
        assertThat(calc.calculate(input(40, true, 12, 0, 12)).accountPeriod().score()).isEqualTo(3);
        assertThat(calc.calculate(input(40, true, 12, 0, 24)).accountPeriod().score()).isEqualTo(4);
    }

    @Test
    @DisplayName("통장 15년(180개월) 이상은 17점 상한")
    void accountCap() {
        assertThat(calc.calculate(input(50, true, 12, 0, 180)).accountPeriod().score()).isEqualTo(17);
        assertThat(calc.calculate(input(50, true, 12, 0, 400)).accountPeriod().score()).isEqualTo(17);
        assertThat(calc.calculate(input(50, true, 12, 0, 168)).accountPeriod().score()).isEqualTo(16); // 14년
    }

    // ── 합계 / 만점 ──────────────────────────────────────────────────
    @Test
    @DisplayName("만점 케이스 — 84점")
    void perfectScore() {
        GeneralSupplyScore score = calc.calculate(input(50, true, 180, 6, 180));
        assertThat(score.total()).isEqualTo(84);
        assertThat(score.isComplete()).isTrue();
        assertThat(score.missingInputs()).isEmpty();
    }

    @Test
    @DisplayName("합계는 세 항목의 단순 합")
    void totalIsSum() {
        GeneralSupplyScore score = calc.calculate(input(40, true, 60, 2, 84));
        // 무주택 5년 → 12, 부양 2명 → 15, 통장 7년 → 9
        assertThat(score.houselessPeriod().score()).isEqualTo(12);
        assertThat(score.dependents().score()).isEqualTo(15);
        assertThat(score.accountPeriod().score()).isEqualTo(9);
        assertThat(score.total()).isEqualTo(36);
    }

    // ── 입력 부족 ────────────────────────────────────────────────────
    @Test
    @DisplayName("항목이 null 이면 산정 불가로 남기고 총점을 안 낸다 (추측 금지)")
    void missingInputsNotGuessed() {
        GeneralSupplyScore score = calc.calculate(input(40, true, null, 2, null));

        assertThat(score.houselessPeriod().score()).isNull();
        assertThat(score.accountPeriod().score()).isNull();
        assertThat(score.dependents().score()).isEqualTo(15);
        assertThat(score.total()).isNull();
        assertThat(score.isComplete()).isFalse();
        assertThat(score.missingInputs()).containsExactly("무주택 기간", "청약통장 가입 기간");
    }

    @Test
    @DisplayName("같은 입력이면 항상 같은 결과 — 결정론성")
    void deterministic() {
        GeneralSupplyInput in = input(37, true, 77, 3, 90);
        GeneralSupplyScore first = calc.calculate(in);
        for (int i = 0; i < 20; i++) {
            assertThat(calc.calculate(in).total()).isEqualTo(first.total());
        }
    }
}
