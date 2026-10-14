package com.portfolio.chungyak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

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

    /**
     * 공고문 본문(첨부 PDF 추출 or PAN_DTL_CTS) <b>전문</b> (#29). 앞 {@link #PART_LEN} 자는 raw_text 에, 나머지는
     * {@link AnnouncementDocumentPart} 에 같은 크기로 나눠 담는다 — MariaDB 행 상한 때문에 한 컬럼에 다 못 넣는다.
     * 예전에는 15,500자({@link #LEGACY_CAP})에서 잘랐다: LH 공고문 전문은 30,000~68,000자라 23~52% 만 남아
     * 제출 서류·소득 기준 같은 뒷부분이 검색되지 않았다. 전체 상한 {@link #MAX_LEN} 은 방어용이다.
     */
    public static final int PART_LEN = 15_000;
    /** 표를 행 단위로 펴면(#42) 위치순보다 20~30% 길다 — 청약홈 2026000463 은 216,033자 */
    public static final int MAX_LEN = 300_000;
    /** 예전 수집의 절단 길이 — 이 길이에 조각이 없으면 잘린 원문으로 보고 다시 받는다 */
    public static final int LEGACY_CAP = 15_500;
    /** 지금 추출기가 만드는 원문 형식 — {@code PdfNoticeExtractor} 를 바꾸면 올린다 */
    public static final int CURRENT_FORMAT = 2;

    /** 원문의 앞 {@link #PART_LEN} 자 (예전 수집분은 앞 15,500자) */
    @Column(name = "raw_text", nullable = false, length = 16_000)
    private String firstPart;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "document_id", nullable = false)
    @OrderBy("partIndex")
    private List<AnnouncementDocumentPart> parts = new ArrayList<>();

    @Column(name = "text_hash", nullable = false, length = 64)
    private String textHash;

    /**
     * 원문 추출 형식 (#42) — 1 = 위치순 추출(표 칸이 섞인다), 2 = 표를 행 단위 문장으로 편 추출.
     * {@link #CURRENT_FORMAT} 보다 낮으면 접수 전·중 공고는 다음 수집에서 PDF 를 다시 받아 교체한다.
     */
    @Column(name = "text_format", nullable = false)
    private int textFormat;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public AnnouncementDocument(Long announcementId, String source, String rawText, Instant fetchedAt) {
        this.announcementId = announcementId;
        this.source = source;
        replaceText(rawText, fetchedAt);
    }

    /** 원문 전문 — 조각을 이어 붙인다 */
    public String getRawText() {
        if (parts.isEmpty()) return firstPart;
        StringBuilder sb = new StringBuilder(firstPart);
        parts.forEach(p -> sb.append(p.getContent()));
        return sb.toString();
    }

    /** 지금 추출기보다 예전 형식의 원문인지 — 접수 전·중 공고면 다시 받는다(#42 백필) */
    public boolean isOutdatedFormat() {
        return textFormat < CURRENT_FORMAT;
    }

    /** 예전 수집(15,500자 절단)으로 잘린 원문인지 — 다시 받아야 한다(#29 백필) */
    public boolean isLegacyTruncated() {
        return parts.isEmpty() && firstPart.length() == LEGACY_CAP;
    }

    /**
     * 원문 교체 — 해시가 바뀌면 인덱서가 재인덱싱한다. 조각은 <b>있던 행을 재사용</b>한다: 지우고 새로 넣으면
     * Hibernate 가 삽입을 삭제보다 먼저 내보내 (document_id, part_index) 유일 제약에 걸린다.
     */
    public void replaceText(String rawText, Instant fetchedAt) {
        String text = rawText.length() <= MAX_LEN ? rawText : rawText.substring(0, MAX_LEN);
        this.firstPart = text.substring(0, Math.min(PART_LEN, text.length()));
        List<String> rest = new ArrayList<>();
        for (int i = PART_LEN; i < text.length(); i += PART_LEN) {
            rest.add(text.substring(i, Math.min(i + PART_LEN, text.length())));
        }
        for (int i = 0; i < rest.size(); i++) {
            if (i < parts.size()) parts.get(i).replaceContent(rest.get(i));
            else parts.add(new AnnouncementDocumentPart(i, rest.get(i)));
        }
        while (parts.size() > rest.size()) parts.remove(parts.size() - 1);
        this.textHash = sha256(text);
        this.fetchedAt = fetchedAt;
        this.textFormat = CURRENT_FORMAT;   // 교체는 늘 지금 추출기가 만든 글로 한다
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
