package com.portfolio.chungyak.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 공고문 금액 꺼내기 — 문장은 2026-11-10 LH 분양 공고문 원문(시흥거모 S-1, 남양주왕숙 A-2) 그대로 */
class NoticeFiguresTest {

    private static final String INCOME = "[단위 : 원] 공급유형 구분 3인 이하 4인 5인 6인 7인 8인 도시근로자 가구당 월평균소득액의 100% "
            + "7,533,763 8,802,202 9,326,985 9,906,263 10,485,541 11,064,819 신생아 우선공급";

    @Test
    @DisplayName("소득표 100% 행 — 3인 이하·4~8인 여섯 칸")
    void incomeRow() {
        assertThat(NoticeFigures.incomeRow100(INCOME))
                .containsExactly(7_533_763L, 8_802_202L, 9_326_985L, 9_906_263L, 10_485_541L, 11_064_819L);
        assertThat(NoticeFigures.incomeRow100("전년도 도시근로자 가구당 월평균소득의 100%\n 7,533,763 8,802,202\n9,326,985 9,906,263 10,485,541 11,064,819"))
                .hasSize(6);
        // 임대 공고의 "가구원수별 … 100퍼센트 이하" 문장엔 금액 행이 없다
        assertThat(NoticeFigures.incomeRow100("월평균소득 합계가 전년도 도시근로자 가구원수별 가구당 월평균소득의 100퍼센트 이하")).isEmpty();
    }

    @Test
    @DisplayName("'부동산 215,500천원' 꼴 금액 — 원으로")
    void thousandWon() {
        String asset = "자산 부동산 215,500천원 이하 해당 없음 해당 없음 부동산 215,500천원 이하 (부동산, 자동차) 자동차 45,420천원 이하 자동차 49,960천원";
        assertThat(NoticeFigures.thousandWonAfter(asset, "부동산")).containsExactly(215_500_000L);
        assertThat(NoticeFigures.thousandWonAfter(asset, "자동차")).containsExactly(45_420_000L, 49_960_000L);
        assertThat(NoticeFigures.thousandWonAfter("부동산(연금…) 362,000천원", "부동산")).isEmpty();   // 라벨 바로 뒤가 아니면 안 본다
    }

    @Test
    @DisplayName("금액이 적혀 있는지 — 천원·원·억/만원 표기 모두")
    void mentions() {
        assertThat(NoticeFigures.mentionsAmount("총납입액 362,000천원 • 주식", 362_000_000L)).isTrue();
        assertThat(NoticeFigures.mentionsAmount("부동산 가액이 3억 3,100만원 이하", 331_000_000L)).isTrue();
        assertThat(NoticeFigures.mentionsAmount("331,000,000원 이하", 331_000_000L)).isTrue();
        assertThat(NoticeFigures.mentionsAmount("3억 4,500만원 이하", 362_000_000L)).isFalse();
        assertThat(NoticeFigures.mentionsAmount(null, 1L)).isFalse();
    }
}
