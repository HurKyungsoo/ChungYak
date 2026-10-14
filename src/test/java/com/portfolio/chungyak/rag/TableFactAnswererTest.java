package com.portfolio.chungyak.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 표 값 결정론 답변 (#42) — 행은 실제 공고문을 표 추출한 줄 그대로(2026-10-14). */
class TableFactAnswererTest {

    /** 공고 2901 임대 조건 표 */
    private static final String RENT_2901 = """
            ■ 임대주택 임대조건(임대보증금과 월 임대료) [금액단위: 원]
            단지명 (블록): 충북혁신4 (천년나무4) · 주택형 (타입): 059.9800A (59A) · 임대조건 임대보증금 합계: 33,148,000 · 임대조건 임대보증금 계약금 (계약 시): 6,600,000 · 임대조건 임대보증금 잔금 (입주 시): 26,548,000 · 임대조건 월 임대료: 292,150 · 증액 (현행 전환요율 5% 적용 시) 한도액: 42,000,000
            """;

    /** 공고 3000 — 기본 월임대료(166,260)가 이름 없는 칸에 있고, 이름 붙은 임대료는 "최대전환 시"뿐 */
    private static final String RENT_3000 = """
            공급 형별: 21A · 공급 계층: 대학생· 청년(소득無) · 계: 32,708,000 · 계약금 (계약시): 1,635,000 · 잔금 (입주시): 31,073,000 · 166,260 · 최대전환 시 임대조건 월임대료 (원): 111,260 242,090
            """;

    /** 청약홈 2026000492 분양 납부표 + 발코니 확장 납부표 */
    private static final String SALE_492 = """
            약식 표기: 84A · 해 당 세대수: 3 · 공급금액 계: 487,000,000 · 계약금(10%): 48,700,000 · 중도금(60%) 1차(10%) 2027-03-17: 48,700,000
            주택형: 84A · 발코니 확장 금액: 12,000,000 · 계약금(20%): 2,400,000 · 잔금(80%): 9,600,000
            """;

    /** 공고 2997 <표5> 월평균소득 기준 */
    private static final String INCOME_2997 = """
            구분: 전년도 도시근로자 가구당 월평균소득의 130% · 3인 이하: 9,793,892 · 4인: 11,442,863 · 5인: 12,125,081
            구분: 전년도 도시근로자 가구당 월평균소득의 140% (본인 및 배우자가 모두 소득이 있는 경우) · 3인 이하: 10,547,268 · 4인: 12,323,083 · 5인: 13,057,779
            """;

    private static Optional<FactAnswerer.Fact> ask(String text, String q) {
        return TableFactAnswerer.answer(text, q);
    }

    @Test
    @DisplayName("월임대료·보증금 — 조건 없는 '월 임대료' 열이 있는 행을 그대로 (#2901 오답 사례)")
    void rentRow() {
        var fact = ask(RENT_2901, "월 임대료가 얼마예요?");
        assertThat(fact).isPresent();
        assertThat(fact.get().text()).contains("임대조건 월 임대료: 292,150").contains("임대보증금 합계: 33,148,000");
        assertThat(fact.get().source()).isEqualTo(TableFactAnswerer.SOURCE);
        assertThat(ask(RENT_2901, "계약할 때 계약금은 얼마예요?")).get()
                .extracting(FactAnswerer.Fact::text).asString().contains("6,600,000");
    }

    @Test
    @DisplayName("이름 붙은 임대료가 '최대전환 시'뿐이면 답하지 않는다 — 기본 임대료로 오해할 수 있다")
    void qualifiedRentOnlyIsSkipped() {
        assertThat(ask(RENT_3000, "대학생 월임대료는 얼마예요?")).isEmpty();
    }

    @Test
    @DisplayName("계약금 비율 — 분양 납부표 열 이름에서. 발코니 확장 납부표의 다른 비율은 세지 않는다")
    void downPaymentPercent() {
        var fact = ask(SALE_492, "계약금은 몇 퍼센트예요?");
        assertThat(fact).isPresent();
        assertThat(fact.get().text()).startsWith("계약금은 공급금액의 10%입니다").contains("48,700,000");
        // 발코니 계약금을 물으면 분양 계약금으로 답하지 않는다
        assertThat(ask(SALE_492, "발코니 확장 계약금은 얼마예요?")).isEmpty();
    }

    @Test
    @DisplayName("분양 납부표에 계약금 비율이 둘 이상이면 넘긴다")
    void ambiguousPercentIsSkipped() {
        String two = SALE_492 + "약식 표기: 59A · 공급금액 계: 300,000,000 · 계약금(5%): 15,000,000 · 중도금(60%) 1차(10%): 30,000,000\n";
        assertThat(ask(two, "계약금은 몇 퍼센트예요?")).isEmpty();
    }

    @Test
    @DisplayName("소득 기준 — 가구원 수가 열 이름인 표의 행")
    void incomeRows() {
        var fact = ask(INCOME_2997, "4인 가구 소득 기준이 얼마예요?");
        assertThat(fact).isPresent();
        assertThat(fact.get().text()).contains("4인: 11,442,863").contains("4인: 12,323,083");
    }

    @Test
    @DisplayName("방법·가능 질문, 예전 형식 원문(이름: 값 행 없음)에는 답하지 않는다")
    void skipsNonAmountQuestionsAndOldFormat() {
        assertThat(ask(RENT_2901, "월세는 어떻게 정해져요?")).isEmpty();
        assertThat(ask(RENT_2901, "보증금을 올리고 월세를 줄일 수 있나요?")).isEmpty();
        assertThat(ask("059.9800A 33,148,000 6,600,000 26,548,000 292,150\n임대보증금 월임대료", "월 임대료가 얼마예요?")).isEmpty();
        assertThat(ask(null, "월 임대료가 얼마예요?")).isEmpty();
    }

    @Test
    @DisplayName("행이 많으면 앞의 몇 행만 — 나머지는 원문으로")
    void capsRows() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TableFactAnswerer.MAX_ROWS + 3; i++) {
            sb.append("주택형 (타입): 0").append(i).append("A · 임대조건 월 임대료: 29").append(i).append(",150\n");
        }
        var fact = ask(sb.toString(), "월 임대료가 얼마예요?");
        assertThat(fact).isPresent();
        assertThat(fact.get().text()).contains("외 3행");
    }
}
