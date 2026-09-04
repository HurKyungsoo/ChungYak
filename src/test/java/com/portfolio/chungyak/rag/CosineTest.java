package com.portfolio.chungyak.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CosineTest {

    @Test
    @DisplayName("같은 방향 = 1, 직교 = 0, 반대 = -1")
    void basics() {
        assertThat(Cosine.similarity(new float[]{1, 2, 3}, new float[]{2, 4, 6})).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(Cosine.similarity(new float[]{1, 0}, new float[]{0, 1})).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(Cosine.similarity(new float[]{1, 1}, new float[]{-1, -1})).isCloseTo(-1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("영벡터·길이 불일치·null 은 0")
    void degenerate() {
        assertThat(Cosine.similarity(new float[]{0, 0}, new float[]{1, 1})).isZero();
        assertThat(Cosine.similarity(new float[]{1, 2}, new float[]{1, 2, 3})).isZero();
        assertThat(Cosine.similarity(null, new float[]{1})).isZero();
    }
}
