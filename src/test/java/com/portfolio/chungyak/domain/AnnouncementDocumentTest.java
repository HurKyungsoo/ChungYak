package com.portfolio.chungyak.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** 공고문 원문 전문 저장 (#29) — 앞 15,000자는 raw_text, 나머지는 같은 크기 조각으로. */
class AnnouncementDocumentTest {

    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");

    private static String text(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; sb.length() < len; i++) sb.append("공고문").append(i % 10);
        return sb.substring(0, len);
    }

    @Test
    @DisplayName("전문을 앞 15,000자 + 15,000자 조각으로 나누고, 이어 붙이면 원문 그대로 — 해시는 전문 기준")
    void splitsAndRejoins() {
        String full = text(45_000);
        AnnouncementDocument doc = new AnnouncementDocument(1L, "LH", full, NOW);

        assertThat(doc.getFirstPart()).hasSize(15_000);
        assertThat(doc.getParts()).hasSize(2);
        assertThat(doc.getParts()).extracting(AnnouncementDocumentPart::getPartIndex).containsExactly(0, 1);
        assertThat(doc.getRawText()).isEqualTo(full);
        assertThat(doc.getTextHash()).isEqualTo(AnnouncementDocument.sha256(full));
    }

    @Test
    @DisplayName("짧은 원문은 조각 없이 raw_text 하나")
    void shortTextHasNoParts() {
        AnnouncementDocument doc = new AnnouncementDocument(1L, "LH", "짧은 공고문", NOW);

        assertThat(doc.getParts()).isEmpty();
        assertThat(doc.getRawText()).isEqualTo("짧은 공고문");
    }

    @Test
    @DisplayName("원문 교체는 있던 조각 행을 재사용한다 — 지우고 다시 넣으면 유일 제약에 걸린다")
    void replaceReusesParts() {
        AnnouncementDocument doc = new AnnouncementDocument(1L, "LH", text(45_000), NOW);
        AnnouncementDocumentPart first = doc.getParts().get(0);

        doc.replaceText(text(20_000), NOW);
        assertThat(doc.getParts()).hasSize(1);
        assertThat(doc.getParts().get(0)).isSameAs(first);
        assertThat(doc.getRawText()).isEqualTo(text(20_000));

        doc.replaceText(text(10_000), NOW);
        assertThat(doc.getParts()).isEmpty();
    }

    @Test
    @DisplayName("방어 상한 200,000자")
    void capsAtMaxLen() {
        AnnouncementDocument doc = new AnnouncementDocument(1L, "LH", text(250_000), NOW);

        assertThat(doc.getRawText()).hasSize(AnnouncementDocument.MAX_LEN);
    }

    @Test
    @DisplayName("예전 수집(15,500자에서 잘림, 조각 없음)만 잘린 원문으로 본다 — 백필 대상")
    void detectsLegacyTruncation() {
        assertThat(new AnnouncementDocument(1L, "LH", text(45_000), NOW).isLegacyTruncated()).isFalse();
        assertThat(new AnnouncementDocument(1L, "LH", text(15_000), NOW).isLegacyTruncated()).isFalse();

        AnnouncementDocument legacy = new AnnouncementDocument(1L, "LH", text(100), NOW);
        ReflectionTestUtils.setField(legacy, "firstPart", text(AnnouncementDocument.LEGACY_CAP));
        assertThat(legacy.isLegacyTruncated()).isTrue();
    }
}
