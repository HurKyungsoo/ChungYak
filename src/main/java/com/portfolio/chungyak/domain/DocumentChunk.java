package com.portfolio.chungyak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 공고문 원문을 검색 단위로 쪼갠 한 조각 + 그 임베딩 벡터.
 *
 * {@code (announcement_id, chunk_index)} 가 유일. {@code sourceTextHash} 는 이 청크가
 * 어느 원문 판본에서 나왔는지 — 원문이 바뀌면 인덱서가 이 행들을 지우고 다시 만든다.
 */
@Entity
@Table(name = "document_chunk",
        uniqueConstraints = @UniqueConstraint(name = "uk_document_chunk", columnNames = {"announcement_id", "chunk_index"}),
        indexes = @Index(name = "idx_document_chunk_ann", columnList = "announcement_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "announcement_id", nullable = false)
    private Long announcementId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, length = 2000)
    private String content;

    @Convert(converter = FloatArrayJsonConverter.class)
    @Column(nullable = false, length = 20000)
    private float[] embedding;

    @Column(name = "embedding_model", nullable = false, length = 40)
    private String embeddingModel;

    @Column(name = "source_text_hash", nullable = false, length = 64)
    private String sourceTextHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DocumentChunk(Long announcementId, int chunkIndex, String content, float[] embedding,
                         String embeddingModel, String sourceTextHash, Instant createdAt) {
        this.announcementId = announcementId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embedding = embedding;
        this.embeddingModel = embeddingModel;
        this.sourceTextHash = sourceTextHash;
        this.createdAt = createdAt;
    }
}
