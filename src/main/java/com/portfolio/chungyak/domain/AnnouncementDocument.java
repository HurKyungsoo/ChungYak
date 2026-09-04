package com.portfolio.chungyak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 한 공고의 입주자모집공고문 원문(비정형 텍스트).
 *
 * 벡터 검색 대상이다 — 판정에는 쓰지 않는다(판정은 rule 엔진의 결정론 영역).
 * {@code announcement} 행과 1:1. 원문이 커서(LH 공고내용 4,000자+) 별도 테이블로 뺀다.
 * {@code textHash} 로 원문 변경을 감지해 재인덱싱 여부를 정한다.
 */
@Entity
@Table(name = "announcement_document",
        uniqueConstraints = @UniqueConstraint(name = "uk_announcement_document", columnNames = "announcement_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnnouncementDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "announcement_id", nullable = false)
    private Long announcementId;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "raw_text", nullable = false, length = 20000)
    private String rawText;

    @Column(name = "text_hash", nullable = false, length = 64)
    private String textHash;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public AnnouncementDocument(Long announcementId, String source, String rawText, Instant fetchedAt) {
        this.announcementId = announcementId;
        this.source = source;
        this.rawText = rawText;
        this.textHash = sha256(rawText);
        this.fetchedAt = fetchedAt;
    }

    /** 원문 교체 — 해시가 바뀌면 인덱서가 재인덱싱한다. */
    public void replaceText(String rawText, Instant fetchedAt) {
        this.rawText = rawText;
        this.textHash = sha256(rawText);
        this.fetchedAt = fetchedAt;
    }

    public static String sha256(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
