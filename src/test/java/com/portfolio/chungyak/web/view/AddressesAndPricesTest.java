package com.portfolio.chungyak.web.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 목록 카드용 짧은 표기 — 시·군·구, 분양가 범위 */
class AddressesAndPricesTest {

    @Test
    @DisplayName("주소에서 시·도 다음 시·군·구 하나만 — 없으면 null")
    void district() {
        assertThat(Addresses.district("서울특별시 서초구 반포동 1-1")).isEqualTo("서초구");
        assertThat(Addresses.district("경기도 고양시 덕양구 용두동 1")).isEqualTo("고양시");
        assertThat(Addresses.district("전남광주통합특별시 북구 임동 100-1 일원")).isEqualTo("북구");
        assertThat(Addresses.district("충청북도 청주시 흥덕구 송절동")).isEqualTo("청주시");
        assertThat(Addresses.district("세종특별자치시 산울동 1")).isNull();
        assertThat(Addresses.district("경기도 양평군 양평읍")).isEqualTo("양평군");
        assertThat(Addresses.district(null)).isNull();
    }

    @Test
    @DisplayName("분양가 짧은 표기 — 억은 소수 한 자리, 1억 미만은 만")
    void compactPrices() {
        assertThat(Prices.compactManwon(87000)).isEqualTo("8.7억");
        assertThat(Prices.compactManwon(50000)).isEqualTo("5억");
        assertThat(Prices.compactManwon(283500)).isEqualTo("28.4억");   // 반올림
        assertThat(Prices.compactManwon(5000)).isEqualTo("5,000만");
        assertThat(Prices.compactManwon(null)).isNull();
        assertThat(Prices.compactRange(52000, 87000)).isEqualTo("5.2억~8.7억");
        assertThat(Prices.compactRange(87000, 87000)).isEqualTo("8.7억");
        assertThat(Prices.compactRange(null, null)).isNull();
    }
}
