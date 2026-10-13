package com.portfolio.chungyak.repository;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.AnnouncementDocument;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공고문 원문 조각 저장 (#29) — 실제 Hibernate + Flyway(V8) 로 저장·교체가 유일 제약·FK 에 걸리지 않는지.
 * 조각을 지우고 새로 넣으면 Hibernate 가 삽입을 먼저 내보내 (document_id, part_index) 유일 제약에 걸린다 —
 * 엔티티가 행을 재사용하는지 flush 로 확인한다.
 */
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:doc-part-test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AnnouncementDocumentRepositoryTest {

    @Autowired private AnnouncementDocumentRepository documents;
    @Autowired private AnnouncementRepository announcements;
    @Autowired private EntityManager em;

    private static String text(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; sb.length() < len; i++) sb.append("공고문 본문 ").append(i);
        return sb.substring(0, len);
    }

    @Test
    @DisplayName("전문 저장 → 다시 읽어 이어 붙이면 원문 그대로, 길이를 늘리고 줄여 교체해도 제약에 걸리지 않는다")
    void savesAndReplacesFullText() {
        Long annId = announcements.save(Announcement.builder()
                .externalId("LH-T29").houseManageNo("T29").pblancNo("T29").houseName("테스트")
                .houseType(com.portfolio.chungyak.domain.HouseType.APT)
                .houseDetailType(com.portfolio.chungyak.domain.HouseDetailType.PUBLIC)
                .regionName("서울")
                .regulationFlags(com.portfolio.chungyak.domain.RegulationFlags.builder().build()).build()).getId();
        String full = text(40_000);
        documents.save(new AnnouncementDocument(annId, "LH", full, Instant.now()));
        em.flush();
        em.clear();

        AnnouncementDocument loaded = documents.findByAnnouncementId(annId).orElseThrow();
        assertThat(loaded.getRawText()).isEqualTo(full);
        assertThat(loaded.getParts()).hasSize(2);

        for (int len : new int[]{35_000, 50_000, 10_000, 31_000}) {
            AnnouncementDocument doc = documents.findByAnnouncementId(annId).orElseThrow();
            doc.replaceText(text(len), Instant.now());
            em.flush();
            em.clear();
            assertThat(documents.findByAnnouncementId(annId).orElseThrow().getRawText()).isEqualTo(text(len));
        }
    }
}
