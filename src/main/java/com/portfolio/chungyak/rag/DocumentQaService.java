package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.rag.VectorSearch.Hit;
import com.portfolio.chungyak.repository.DocumentChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 공고문 Q&A — "이 공고 잔여세대 신청 조건이 뭐야?" 같은 질문에 공고문 근거로 답한다.
 *
 * 흐름: 질문 → {@link VectorSearch#searchWithin} 로 관련 발췌 → {@link DocumentAnswerer} 가 답변.
 * 판정이 아니라 정보 검색이다(방향 3). 답변은 항상 발췌 근거와 함께 화면에 표시된다.
 *
 * 임베딩(Voyage) 또는 응답기(Anthropic) 중 하나라도 없으면 DISABLED — 화면에서 입력창을 숨긴다.
 */
@Slf4j
@Service
public class DocumentQaService {

    private final VectorSearch vectorSearch;
    private final DocumentChunkRepository chunkRepository;
    private final Optional<DocumentAnswerer> answerer;
    private final int contextChunks;
    private final double minScore;
    private final double keywordMinScore;

    public DocumentQaService(VectorSearch vectorSearch,
                             DocumentChunkRepository chunkRepository,
                             Optional<DocumentAnswerer> answerer,
                             RagProperties properties) {
        this.vectorSearch = vectorSearch;
        this.chunkRepository = chunkRepository;
        this.answerer = answerer;
        this.contextChunks = properties.qa().contextChunks();
        this.minScore = properties.qa().minScore();
        this.keywordMinScore = properties.qa().keywordMinScore();
    }

    public boolean isEnabled() {
        return vectorSearch.isAvailable() && answerer.isPresent();
    }

    /** 이 공고에 인덱싱된 공고문 청크가 있는지 — 화면에서 입력창 노출 여부. */
    public boolean hasIndex(Long announcementId) {
        return chunkRepository.existsByAnnouncementId(announcementId);
    }

    public enum Status { DISABLED, NO_INDEX, NO_MATCH, ANSWERED }

    public record Citation(int chunkIndex, String excerpt, double score, double keywordScore) {}

    public record QaAnswer(Status status, String answer, List<Citation> citations) {
        public boolean isShown() { return status != Status.DISABLED; }
        public boolean hasAnswer() { return status == Status.ANSWERED; }

        static QaAnswer disabled()  { return new QaAnswer(Status.DISABLED, null, List.of()); }
        static QaAnswer noIndex()   { return new QaAnswer(Status.NO_INDEX, null, List.of()); }
        static QaAnswer noMatch(List<Citation> c) { return new QaAnswer(Status.NO_MATCH, null, c); }
        static QaAnswer answered(String a, List<Citation> c) { return new QaAnswer(Status.ANSWERED, a, c); }
    }

    public QaAnswer answer(Long announcementId, String question) {
        if (!isEnabled() || question == null || question.isBlank()) {
            return QaAnswer.disabled();
        }

        List<Hit> hits = vectorSearch.searchWithin(announcementId, question.strip(), contextChunks);
        if (hits.isEmpty()) {
            return QaAnswer.noIndex();
        }

        List<Citation> citations = hits.stream()
                .map(h -> new Citation(h.chunkIndex(), h.content(), h.score(), h.keywordScore()))
                .toList();

        Hit top = hits.get(0);
        if (top.score() < minScore && top.keywordScore() < keywordMinScore) {
            log.info("공고 #{} Q&A — 최고 발췌 코사인 {} · 키워드 {} 로 관련 발췌 없음", announcementId,
                    String.format("%.2f", top.score()), String.format("%.2f", top.keywordScore()));
            return QaAnswer.noMatch(citations);
        }

        List<String> numbered = new java.util.ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            numbered.add("[" + (i + 1) + "] " + hits.get(i).content());
        }

        try {
            String text = answerer.get().answer(question.strip(), numbered);
            return text.isBlank() ? QaAnswer.noMatch(citations) : QaAnswer.answered(text, citations);
        } catch (RuntimeException e) {
            log.warn("공고 #{} Q&A 응답 생성 실패 — {}", announcementId, e.toString());
            return QaAnswer.noMatch(citations);
        }
    }
}
