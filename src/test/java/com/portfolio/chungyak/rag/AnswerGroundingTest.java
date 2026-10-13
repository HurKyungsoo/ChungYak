package com.portfolio.chungyak.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공고문 Q&A 답변의 금액·날짜 근접 근거 검사 (#31). 발췌는 실제 공고문(#2901·#3247)에서 옮긴 형태다.
 */
class AnswerGroundingTest {

    /** #2901 충북혁신4 — PDF 표가 텍스트로 뽑혀 열 구분이 사라진 발췌. 실제 월임대료는 292,150원(공급 API MM_RFE) */
    private static final List<String> TABLE = List.of(
            "[1] 증금 임대료 한도액 보증금 임대료 059.9800A 33,148,000 6,600,000 26,548,000 292,150 42,000,000 "
                    + "75,148,000 117,150 -24,000,000 9,148,000 362,150 (59A) 충북혁신4 059.9800X 33,148,000",
            "[2] 충북혁신 4단지 10년 분양전환공공임대주택 [입주자모집공고일 : 2026.10.12.] [임대종료일 2027.07.31.] "
                    + "기존 단지 최초 입주 2017년 7월");

    /** #3247 대전대동2 — 문장으로 적힌 계약금·일정 */
    private static final List<String> SENTENCE = List.of(
            "[1] ■ 청약 및 계약 등 주요일정(2026년) 선착순 동호지정 및 계약 (계약금 1,000만원 정액제) 일시 09.04(목)",
            "[2] 당첨자 발표 : 2026.10.26.(월) 청약플러스에서 확인");

    @Test
    @DisplayName("표 한 줄에서 다른 열 값을 '월 임대료'로 단정 → 차단 (#2901 실제 오답)")
    void blocksTableMisread() {
        assertThat(AnswerGrounding.check("월 임대료는 6,600,000원입니다 [1].", TABLE))
                .get().asString().contains("임대료");
        // 대가: 임대보증금 33,148,000원은 실제로 맞는 값(공급 API LS_GMY)이지만 표에서만 나와 함께 막힌다
        assertThat(AnswerGrounding.check("임대보증금은 33,148,000원입니다 [1].", TABLE)).isPresent();
        assertThat(AnswerGrounding.check("계약금은 292,150원, 잔금은 42,000,000원입니다 [1].", TABLE)).isPresent();
    }

    @Test
    @DisplayName("옛 입주 시기를 신청자 입주로 엮은 날짜 → 입주 바로 뒤의 날짜가 아니면 차단 (#2901)")
    void blocksMisattributedDate() {
        assertThat(AnswerGrounding.check("2017년 7월경 입주가 가능합니다 [2].", TABLE)).isEmpty();   // 발췌에 "입주 2017년 7월" 그대로 있다
        assertThat(AnswerGrounding.check("입주는 2027년 7월에 가능합니다 [2].", TABLE)).isPresent();   // 2027.07 은 임대종료일
    }

    @Test
    @DisplayName("문장에 항목 이름과 함께 적힌 값은 통과 — 단위가 달라도(1,000만원 = 10,000,000원)")
    void passesSentenceGrounded() {
        assertThat(AnswerGrounding.check("계약금은 1,000만원 정액제입니다 [1].", SENTENCE)).isEmpty();
        assertThat(AnswerGrounding.check("계약금은 10,000,000원입니다 [1].", SENTENCE)).isEmpty();
        assertThat(AnswerGrounding.check("당첨자 발표일은 2026년 10월 26일(월)입니다 [2].", SENTENCE)).isEmpty();
        assertThat(AnswerGrounding.check("당첨자 발표는 10월 26일입니다.", SENTENCE)).isEmpty();
    }

    @Test
    @DisplayName("항목 이름은 맞는데 값이 다르면 차단")
    void blocksWrongValueForLabel() {
        assertThat(AnswerGrounding.check("계약금은 2,000만원입니다.", SENTENCE)).isPresent();
        assertThat(AnswerGrounding.check("당첨자 발표일은 2026년 10월 27일입니다.", SENTENCE)).isPresent();
    }

    @Test
    @DisplayName("항목 이름 없이 나온 금액·날짜는 발췌 어딘가에 있으면 통과, 없으면(지어낸 값) 차단")
    void unlabeledMustExist() {
        assertThat(AnswerGrounding.check("일정은 2026.10.26.(월)입니다.", SENTENCE)).isEmpty();
        assertThat(AnswerGrounding.check("총 5,000만원이 필요합니다.", SENTENCE)).isPresent();
    }

    @Test
    @DisplayName("금액·날짜가 없는 답과 '확인되지 않습니다' 답은 통과 — 개월·세대·면적은 대상이 아니다")
    void ignoresNonClaims() {
        assertThat(AnswerGrounding.check("공고문 발췌에서는 확인되지 않습니다. 원문을 확인하세요.", TABLE)).isEmpty();
        assertThat(AnswerGrounding.check("만 19세 이상 무주택세대구성원이 신청할 수 있습니다 [1].", TABLE)).isEmpty();
        assertThat(AnswerGrounding.check("청약통장을 12개월 이상 유지해야 합니다.", TABLE)).isEmpty();
    }

    @Test
    @DisplayName("좁은 줄바꿈 없는 공백(U+202F)이 섞여도 날짜를 인식한다 — gpt-oss 가 쓰는 공백이라 놓치면 검사가 통째로 빠진다 (#31 실사용)")
    void narrowNoBreakSpaceIsSpace() {
        List<String> ex = List.of("[1] [임대종료일 2027.07.31.] 만기분양전환 2027.08월 예정");

        assertThat(AnswerGrounding.check("입주 가능한 시기는 2027 년 8 월 1 일부터입니다.", ex)).isPresent();
        // 줄바꿈이 문장 중간에 있어도 같은 문장
        assertThat(AnswerGrounding.check("실제 입주 가능 시기는\n2027년 8월부터입니다.", ex)).isPresent();
    }
}
