package com.portfolio.chungyak.web.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실데이터(청약홈 MDHS_TELNO 2,875건)에서 실제로 나온 자릿수만 다룬다.
 * 규칙 밖의 값은 끊지 않고 그대로 두는지도 함께 본다.
 */
class PhoneNumbersTest {

    @Test
    @DisplayName("8자리 대표번호 — 1522-0125")
    void representativeNumber() {
        assertThat(PhoneNumbers.format("15220125")).isEqualTo("1522-0125");
    }

    @Test
    @DisplayName("9자리 서울 — 02-303-3457")
    void seoulNineDigits() {
        assertThat(PhoneNumbers.format("023033457")).isEqualTo("02-303-3457");
    }

    @Test
    @DisplayName("10자리 — 서울은 02-2015-1045, 그 외 지역은 032-613-2355")
    void tenDigitsSplitByAreaCode() {
        assertThat(PhoneNumbers.format("0220151045")).isEqualTo("02-2015-1045");
        assertThat(PhoneNumbers.format("0326132355")).isEqualTo("032-613-2355");
    }

    @Test
    @DisplayName("11자리 — 031-8094-1260, 010-2085-0222")
    void elevenDigits() {
        assertThat(PhoneNumbers.format("03180941260")).isEqualTo("031-8094-1260");
        assertThat(PhoneNumbers.format("01020850222")).isEqualTo("010-2085-0222");
    }

    @Test
    @DisplayName("이미 하이픈이 있으면 손대지 않는다")
    void alreadyFormattedIsKept() {
        assertThat(PhoneNumbers.format("080-783-3000")).isEqualTo("080-783-3000");
    }

    @Test
    @DisplayName("규칙에 없는 자릿수는 원본 그대로 — 잘못 끊어 읽히느니 낫다")
    void unknownLengthIsKept() {
        assertThat(PhoneNumbers.format("1234567")).isEqualTo("1234567");
        assertThat(PhoneNumbers.format("123456789012345")).isEqualTo("123456789012345");
    }

    @Test
    @DisplayName("null·공백은 그대로 — 화면에서 th:if 로 거른다")
    void nullAndBlankPassThrough() {
        assertThat(PhoneNumbers.format(null)).isNull();
        assertThat(PhoneNumbers.format("  ")).isEqualTo("  ");
    }
}
