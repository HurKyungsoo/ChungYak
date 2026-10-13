package com.portfolio.chungyak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공고문 원문의 뒷부분 조각 (#29) — {@link AnnouncementDocument} 의 raw_text 에 들어가지 않는 나머지를
 * {@link AnnouncementDocument#PART_LEN} 자씩 순서대로 담는다. MariaDB 행 상한(65,535B, utf8mb4 4B/자) 때문에
 * 한 컬럼에 전문을 넣을 수 없어 나눈다.
 */
@Entity
@Table(name = "announcement_document_part",
        uniqueConstraints = @UniqueConstraint(name = "uk_announcement_document_part",
                columnNames = {"document_id", "part_index"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnnouncementDocumentPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "part_index", nullable = false)
    private int partIndex;

    @Column(nullable = false, length = 15_000)
    private String content;

    AnnouncementDocumentPart(int partIndex, String content) {
        this.partIndex = partIndex;
        this.content = content;
    }

    void replaceContent(String content) {
        this.content = content;
    }
}
