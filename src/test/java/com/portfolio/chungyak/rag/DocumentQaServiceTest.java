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
            new RagProperties.Chunk(900, 150), new RagProperties.Search(5, 0.6),
            new RagProperties.Qa(4, 0.25, 0.5));

    @BeforeEach
    void setUp() {
        search = mock(VectorSearch.class);
        chunkRepo = mock(DocumentChunkRepository.class);
        lastExcerpts = new AtomicReference<>();
        when(search.isSemantic()).thenReturn(true);
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
    @DisplayName("코사인·키워드 둘 다 임계값 미만 → NO_MATCH, LLM 호출 안 함, 발췌는 보여준다")
    void lowRelevanceDoesNotCallLlm() {
        when(search.searchWithin(eq(1L), anyString(), anyInt())).thenReturn(List.of(
                new Hit(1L, 3, "관련 없는 내용", 0.11, 0.0),
                new Hit(1L, 7, "이것도 관련 없음", 0.05, 0.0)));

        QaAnswer a = service(Optional.of(answerer("불려선 안 됨"))).answer(1L, "발코니 확장비");

        assertThat(a.status()).isEqualTo(Status.NO_MATCH);
        assertThat(a.citations()).hasSize(2);
        assertThat(lastExcerpts.get()).isNull();   // LLM 미호출
    }

    @Test
    @DisplayName("코사인은 낮아도 키워드가 정확히 맞으면 → ANSWERED (키워드 탈출구)")
    void keywordMatchPassesGate() {
        when(search.searchWithin(eq(1L), anyString(), anyInt())).thenReturn(List.of(
                new Hit(1L, 2, "발코니 확장 비용은 세대당 별도 부과", 0.14, 0.9)));

        QaAnswer a = service(Optional.of(answerer("발코니 확장 비용은 별도입니다 [1]."))).answer(1L, "발코니 확장 비용");

        assertThat(a.status()).isEqualTo(Status.ANSWERED);
        assertThat(lastExcerpts.get()).containsExactly("[1] 발코니 확장 비용은 세대당 별도 부과");
    }

    @Test
    @DisplayName("BM25 모드(임베딩 없음) — 코사인은 늘 0 이라 키워드 겹침만으로 관련 여부를 가른다 (#22)")
    void bm25ModeGatesOnKeywordOnly() {
        when(search.isSemantic()).thenReturn(false);
        when(search.searchWithin(eq(1L), anyString(), anyInt())).thenReturn(List.of(
                new Hit(1L, 2, "발코니 확장 비용은 세대당 별도 부과", 0.0, 0.9)));
        DocumentQaService svc = service(Optional.of(answerer("발코니 확장 비용은 별도입니다 [1].")));

        assertThat(svc.isEnabled()).as("임베딩 없이도 응답기만 있으면 켜진다").isTrue();
        assertThat(svc.answer(1L, "발코니 확장 비용").status()).isEqualTo(Status.ANSWERED);

        when(search.searchWithin(eq(1L), anyString(), anyInt())).thenReturn(List.of(
                new Hit(1L, 5, "당첨자 발표 안내", 0.0, 0.2)));
        assertThat(svc.answer(1L, "발코니 확장 비용").status()).isEqualTo(Status.NO_MATCH);
    }

    @Test
    @DisplayName("관련 발췌가 있으면 → ANSWERED, 번호 붙인 발췌를 넘기고 답+근거를 함께 반환")
    void answersWithCitations() {
        when(search.searchWithin(eq(1L), anyString(), anyInt())).thenReturn(List.of(
                new Hit(1L, 2, "잔여세대는 무순위로 접수하며 무주택 요건만 확인합니다.", 0.82, 0.5),
                new Hit(1L, 5, "잔여세대 신청은 마감 이후 홈페이지에 별도 공고합니다.", 0.61, 0.4)));

        QaAnswer a = service(Optional.of(answerer("잔여세대는 무순위·무주택 요건으로 접수합니다 [1]."))).answer(1L, "잔여세대 조건");

        assertThat(a.status()).isEqualTo(Status.ANSWERED);
        assertThat(a.answer()).contains("무순위");
        assertThat(a.citations()).extracting(DocumentQaService.Citation::chunkIndex).containsExactly(2, 5);
        assertThat(lastExcerpts.get()).containsExactly(
                "[1] 잔여세대는 무순위로 접수하며 무주택 요건만 확인합니다.",
                "[2] 잔여세대 신청은 마감 이후 홈페이지에 별도 공고합니다.");
    }

    @Test
    @DisplayName("응답기가 예외를 던지면 → ANSWER_FAILED (NO_MATCH 와 구분, 발췌는 유지)")
    void answererFailureIsGraceful() {
        when(search.searchWithin(eq(1L), anyString(), anyInt())).thenReturn(List.of(
                new Hit(1L, 0, "관련 높은 발췌", 0.9, 0.3)));
        DocumentAnswerer boom = (q, e) -> { throw new RuntimeException("503"); };

        QaAnswer a = service(Optional.of(boom)).answer(1L, "질문");

        assertThat(a.status()).isEqualTo(Status.ANSWER_FAILED);
        assertThat(a.citations()).hasSize(1);
    }

    @Test
    @DisplayName("응답기가 빈 문자열을 돌려주면 → ANSWER_FAILED (관련 발췌는 찾았으니 NO_MATCH 가 아님)")
    void blankAnswerIsAnswerFailedNotNoMatch() {
        when(search.searchWithin(eq(1L), anyString(), anyInt())).thenReturn(List.of(
                new Hit(1L, 0, "관련 높은 발췌", 0.9, 0.3)));

        QaAnswer a = service(Optional.of(answerer("  "))).answer(1L, "질문");

        assertThat(a.status()).isEqualTo(Status.ANSWER_FAILED);
        assertThat(a.citations()).hasSize(1);
    }

    @Test
    @DisplayName("인덱스는 있는데 키워드가 하나도 안 걸려 결과가 비면 → NO_MATCH (NO_INDEX 아님, #22)")
    void emptyHitsWithIndexIsNoMatch() {
        when(search.searchWithin(eq(1L), anyString(), anyInt())).thenReturn(List.of());
        when(chunkRepo.existsByAnnouncementId(1L)).thenReturn(true);

        QaAnswer a = service(Optional.of(answerer("불려선 안 됨"))).answer(1L, "반려동물 키워도 되나요?");

        assertThat(a.status()).isEqualTo(Status.NO_MATCH);
        assertThat(lastExcerpts.get()).isNull();
    }

    @Test
    @DisplayName("답변에 금액이 있으면 '원문 표에서 확인' 안내 대상 — 표 열이 섞여 읽히는 문제 (#22)")
    void mentionsAmount() {
        assertThat(new QaAnswer(Status.ANSWERED, "임대보증금은 33,148,000원입니다 [1].", List.of()).mentionsAmount()).isTrue();
        assertThat(new QaAnswer(Status.ANSWERED, "월 임대료는 29만 원 수준입니다.", List.of()).mentionsAmount()).isTrue();
        assertThat(new QaAnswer(Status.ANSWERED, "당첨자 발표는 2026년 10월 26일입니다 [1].", List.of()).mentionsAmount()).isFalse();
        assertThat(new QaAnswer(Status.NO_MATCH, null, List.of()).mentionsAmount()).isFalse();
    }

    @Test
    @DisplayName("금액이 발췌의 그 항목 값이 아니면 UNGROUNDED — 답은 숨기고 발췌는 보여준다, 다시 만들지 않는다 (#31)")
    void ungroundedAnswerIsWithheld() {
        when(search.searchWithin(eq(1L), anyString(), anyInt())).thenReturn(List.of(
                new Hit(1L, 2, "보증금 임대료 059.9800A 33,148,000 6,600,000 292,150", 0.0, 1.0)));
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        DocumentAnswerer misreads = (q, ex) -> { calls.incrementAndGet(); return "월 임대료는 6,600,000원입니다 [1]."; };

        QaAnswer a = service(Optional.of(misreads)).answer(1L, "월 임대료는 얼마인가요?");

        assertThat(a.status()).isEqualTo(Status.UNGROUNDED);
        assertThat(a.answer()).isNull();
        assertThat(a.citations()).hasSize(1);
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("hasIndex 는 청크 존재 여부")
    void hasIndexDelegates() {
        when(chunkRepo.existsByAnnouncementId(9L)).thenReturn(true);
        assertThat(service(Optional.of(answerer("x"))).hasIndex(9L)).isTrue();
    }
}
