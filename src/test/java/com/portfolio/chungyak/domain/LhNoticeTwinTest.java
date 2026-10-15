package com.portfolio.chungyak.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LhNoticeTwinTest {

    @ParameterizedTest(name = "{0} = {2}")
    @DisplayName("운영 DB 의 청약홈·LH 같은 공고 6쌍(2026-10-15) — 지구·하이픈·BL/블록·정정 태그 차이를 지운다")
    @CsvSource(delimiter = '|', textBlock = """
            의정부우정 A2블록 공공분양주택(본청약)                                  | 2026-08-26 | 의정부우정 A-2블록 공공분양주택 입주자모집공고                              | 2026-08-26
            양주회천지구 A-26블록 공공분양주택                                      | 2026-08-27 | 양주회천 A-26BL 공공분양주택 입주자모집공고                                 | 2026-08-27
            남양주진접2지구 A-4블록 신혼희망타운(공공분양) 잔여세대 추가입주자모집공고 | 2026-08-31 | 남양주진접2 A-4블록 신혼희망타운(공공분양) 잔여세대 추가입주자모집 공고      | 2026-08-31
            인천계양지구 A6블록 공공분양주택(본청약)                                | 2026-08-31 | 인천계양 A6블록 공공분양주택 입주자모집공고                                 | 2026-08-31
            시흥하중지구 A-4블록 신혼희망타운(공공분양) 추가입주자모집              | 2026-09-18 | 시흥하중 A-4블록 신혼희망타운(공공분양) 잔여세대 추가입주자모집공고          | 2026-09-18
            인천계양 A17블록 신혼희망타운(공공분양)(본청약)                         | 2026-09-30 | 인천계양 A17블록 신혼희망타운(공공분양) 입주자모집공고                      | 2026-09-30
            """)
    void realPairsMatch(String applyhome, LocalDate d1, String lh, LocalDate d2) {
        assertThat(LhNoticeTwin.sameNotice(applyhome.strip(), d1, lh.strip(), d2)).isTrue();
    }

    @Test
    @DisplayName("정정 태그·지역 머리말이 붙은 LH 이름도 같은 키")
    void tagsIgnored() {
        assertThat(LhNoticeTwin.keyOf("[정정공고]인천계양 A6블록 공공분양주택 입주자모집공고"))
                .isEqualTo(LhNoticeTwin.keyOf("인천계양지구 A6블록 공공분양주택(본청약)"));
        assertThat(LhNoticeTwin.keyOf("[경기북부] 양주회천 A-26BL 공공분양주택")).hasValue("양주회천|A26");
    }

    @Test
    @DisplayName("다른 블록·다른 지구·공고일 2일 차이는 다른 공고")
    void differentNotices() {
        LocalDate d = LocalDate.of(2026, 8, 31);
        assertThat(LhNoticeTwin.sameNotice("남양주진접2지구 A-4블록 신혼희망타운", d, "시흥하중 A-4블록 신혼희망타운", d)).isFalse();
        assertThat(LhNoticeTwin.sameNotice("인천계양지구 A6블록 공공분양주택", d, "인천계양 A17블록 신혼희망타운", d)).isFalse();
        assertThat(LhNoticeTwin.sameNotice("인천계양지구 A6블록 공공분양주택", d, "인천계양 A6블록 공공분양주택", d.plusDays(2))).isFalse();
    }

    @Test
    @DisplayName("블록 번호가 없으면(민간 시행·이름만 있는 단지) 키가 없다 — 짝을 지어내지 않는다")
    void noBlockNoKey() {
        assertThat(LhNoticeTwin.keyOf("광주효천2 LH천년나무6단지 잔여세대 일반매각 공고")).isEmpty();
        assertThat(LhNoticeTwin.keyOf("힐스테이트 고덕엘리스트 A65BL 공공분양주택")).hasValue("힐스테이트고덕엘리스트|A65");
        assertThat(LhNoticeTwin.keyOf(null)).isEmpty();
    }
}
