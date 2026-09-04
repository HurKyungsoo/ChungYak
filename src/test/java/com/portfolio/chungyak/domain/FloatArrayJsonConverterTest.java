package com.portfolio.chungyak.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FloatArrayJsonConverterTest {

    private final FloatArrayJsonConverter converter = new FloatArrayJsonConverter();

    @Test
    @DisplayName("왕복 — float[] → JSON → float[]")
    void roundTrip() {
        float[] v = {0.1f, -0.25f, 3.0f, 0f};
        String json = converter.convertToDatabaseColumn(v);
        assertThat(json).startsWith("[").endsWith("]");
        assertThat(converter.convertToEntityAttribute(json)).containsExactly(v);
    }

    @Test
    @DisplayName("null·빈 문자열은 빈 배열")
    void nullSafe() {
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("[]");
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
    }
}
