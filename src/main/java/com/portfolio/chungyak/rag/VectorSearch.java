package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.domain.DocumentChunk;
import com.portfolio.chungyak.rag.embedding.EmbeddingClient;
import com.portfolio.chungyak.rag.embedding.EmbeddingClient.InputType;
import com.portfolio.chungyak.repository.DocumentChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 공고문 청크에 대한 의미 검색.
 *
 * 판정이 아니라 정보 검색이다 — 여기서 나온 청크는 나중에 근거 인용과 함께
 * LLM 답변의 컨텍스트로 들어간다(다음 슬라이스).
 *
 * 공고 수백 건 규모라 모든 청크를 메모리에 올려 코사인으로 정렬한다.
 * (수천 건을 넘어가면 네이티브 벡터 인덱스로 교체 — RagProperties/저장소만 갈아끼우면 된다.)
 */
@Slf4j
@Service
public class VectorSearch {

    private final DocumentChunkRepository chunkRepository;
    private final Optional<EmbeddingClient> embeddingClient;
    private final int defaultTopK;

    public VectorSearch(DocumentChunkRepository chunkRepository,
                        Optional<EmbeddingClient> embeddingClient,
                        RagProperties properties) {
        this.chunkRepository = chunkRepository;
        this.embeddingClient = embeddingClient;
        this.defaultTopK = properties.search().topK();
    }

    public boolean isAvailable() {
        return embeddingClient.isPresent();
    }

    /** 한 조각의 검색 결과. */
    public record Hit(Long announcementId, int chunkIndex, String content, double score) {}

    public List<Hit> search(String query) {
        return search(query, defaultTopK);
    }

    public List<Hit> search(String query, int topK) {
        if (embeddingClient.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        float[] q = embeddingClient.get().embed(List.of(query.strip()), InputType.QUERY).get(0);

        return chunkRepository.findAll().stream()
                .map(c -> new Hit(c.getAnnouncementId(), c.getChunkIndex(), c.getContent(),
                        Cosine.similarity(q, c.getEmbedding())))
                .sorted(Comparator.comparingDouble(Hit::score).reversed())
                .limit(Math.max(1, topK))
                .toList();
    }

    /** 특정 공고 안에서만 검색 — "이 공고에 질문하기" 화면용. */
    public List<Hit> searchWithin(Long announcementId, String query, int topK) {
        if (embeddingClient.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        List<DocumentChunk> chunks = chunkRepository.findByAnnouncementId(announcementId);
        if (chunks.isEmpty()) return List.of();

        float[] q = embeddingClient.get().embed(List.of(query.strip()), InputType.QUERY).get(0);
        return chunks.stream()
                .map(c -> new Hit(c.getAnnouncementId(), c.getChunkIndex(), c.getContent(),
                        Cosine.similarity(q, c.getEmbedding())))
                .sorted(Comparator.comparingDouble(Hit::score).reversed())
                .limit(Math.max(1, topK))
                .toList();
    }
}
