package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.rag.DocumentQaService.QaAnswer;
import com.portfolio.chungyak.rag.DocumentQaService.Status;
import com.portfolio.chungyak.rag.VectorSearch.Hit;
import com.portfolio.chungyak.repository.DocumentChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Q&A 오케스트레이션 — 실제 임베딩·LLM 없이 검증.
 * 관련 발췌가 없으면 LLM 을 부르지 않고, 답변은 늘 근거 발췌와 함께 나온다.
 */
class DocumentQaServiceTest {

    private VectorSearch search;
    private DocumentChunkRepository chunkRepo;
    private AtomicReference<List<String>> lastExcerpts;
    private final RagProperties props = new RagProperties(
            new RagProperties.Chunk(900, 150), new RagProperties.Search(5),
            new RagProperties.Qa(4, 0.25));

    @BeforeEach
    void setUp() {
        search = mock(VectorSearch.class);
        chunkRepo = mock(DocumentChunkRepository.class);
        lastExcerpts = new AtomicReference<>();
        when(search.isAvailable()).thenReturn(true);
    }

    private DocumentAnswerer answerer(String reply) {
        return (question, excerpts) -> { lastExcerpts.set(excerpts); return reply; };
    }

    private DocumentQaService service(Optional<DocumentAnswerer> a) {
        return new DocumentQaService(search, chunkRepo, a, props);
    }

    @Test
    @DisplayName("응답기 없음 → DISABLED")
    void disabledWithoutAnswerer() {
        assertThat(service(Optional.empty()).answer(1L, "질문").status()).isEqualTo(Status.DISABLED);
    }

    @Test
    @DisplayName("빈 질문 → DISABLED (LLM·검색 호출 안 함)")
    void blankQuestion() {
        DocumentQaService s = service(Optional.of(answerer("x")));
        assertThat(s.answer(1L, "  ").status()).isEqualTo(Status.DISABLED);
        verify(search, never()).searchWithin(anyLong(), anyString(), anyInt());
    }

    @Test
    @DisplayName("이 공고에 인덱스 없음 → NO_INDEX")
    void noIndex() {
        when(search.searchWithin(eq(1L), anyString(), anyInt())).thenReturn(List.of());
        assertThat(service(Optional.of(answerer("x"))).answer(1L, "질문").status())
                .isEqualTo(Status.NO_INDEX);
    }

    @Test
    @DisplayName("최고 유사도가 임계값 미만 → NO_MATCH, LLM 호출 안 함, 발췌는 보여준다")
    void lowSimilarityDoesNotCallLlm() {
        when(search.searchWithin(eq(1L), anyString(), anyInt())).thenReturn(List.of(
                new Hit(1L, 3, "관련 없는 내용", 0.11),
                new Hit(1L, 7, "이것도 관련 없음", 0.05)));

        QaAnswer a = service(Optional.of(answerer("불려선 안 됨"))).answer(1L, "발코니 확장비");

        assertThat(a.status()).isEqualTo(Status.NO_MATCH);
        assertThat(a.citations()).hasSize(2);
        assertThat(lastExcerpts.get()).isNull();   // LLM 미호출
    }

    @Test
    @DisplayName("관련 발췌가 있으면 → ANSWERED, 번호 붙인 발췌를 넘기고 답+근거를 함께 반환")
    void answersWithCitations() {
        when(search.searchWithin(eq(1L), anyString(), anyInt())).thenReturn(List.of(
                new Hit(1L, 2, "잔여세대는 무순위로 접수하며 무주택 요건만 확인합니다.", 0.82),
                new Hit(1L, 5, "잔여세대 신청은 마감 이후 홈페이지에 별도 공고합니다.", 0.61)));

        QaAnswer a = service(Optional.of(answerer("잔여세대는 무순위·무주택 요건으로 접수합니다 [1]."))).answer(1L, "잔여세대 조건");

        assertThat(a.status()).isEqualTo(Status.ANSWERED);
        assertThat(a.answer()).contains("무순위");
        assertThat(a.citations()).extracting(DocumentQaService.Citation::chunkIndex).containsExactly(2, 5);
        assertThat(lastExcerpts.get()).containsExactly(
                "[1] 잔여세대는 무순위로 접수하며 무주택 요건만 확인합니다.",
                "[2] 잔여세대 신청은 마감 이후 홈페이지에 별도 공고합니다.");
    }

    @Test
    @DisplayName("응답기가 예외를 던지면 → NO_MATCH (발췌는 유지)")
    void answererFailureIsGraceful() {
        when(search.searchWithin(eq(1L), anyString(), anyInt())).thenReturn(List.of(
                new Hit(1L, 0, "관련 높은 발췌", 0.9)));
        DocumentAnswerer boom = (q, e) -> { throw new RuntimeException("503"); };

        QaAnswer a = service(Optional.of(boom)).answer(1L, "질문");

        assertThat(a.status()).isEqualTo(Status.NO_MATCH);
        assertThat(a.citations()).hasSize(1);
    }

    @Test
    @DisplayName("hasIndex 는 청크 존재 여부")
    void hasIndexDelegates() {
        when(chunkRepo.existsByAnnouncementId(9L)).thenReturn(true);
        assertThat(service(Optional.of(answerer("x"))).hasIndex(9L)).isTrue();
    }
}
