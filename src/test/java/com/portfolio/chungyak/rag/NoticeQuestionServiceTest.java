package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.AnnouncementDocument;
import com.portfolio.chungyak.domain.RegulationFlags;
import com.portfolio.chungyak.rag.DocumentQaService.QaAnswer;
import com.portfolio.chungyak.rag.DocumentQaService.Status;
import com.portfolio.chungyak.repository.AnnouncementDocumentRepository;
import com.portfolio.chungyak.service.AnnouncementQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** "이 공고에 물어보기" 입구 (#39) — 구조화 데이터로 답할 수 있으면 LLM 을 부르지 않는다. */
class NoticeQuestionServiceTest {

    @Test
    @DisplayName("요약표·API 로 답할 수 있는 질문은 FACT, AI Q&A 는 부르지 않는다 / 아니면 AI Q&A 로")
    void factFirstThenQa() {
        AnnouncementQueryService queryService = mock(AnnouncementQueryService.class);
        AnnouncementDocumentRepository docs = mock(AnnouncementDocumentRepository.class);
        DocumentQaService qa = mock(DocumentQaService.class);
        when(qa.isEnabled()).thenReturn(true);
        when(queryService.findDetail(1L)).thenReturn(Optional.of(Announcement.builder().externalId("A").houseName("A")
                .regulationFlags(RegulationFlags.builder().build()).build()));
        when(docs.findByAnnouncementId(1L)).thenReturn(Optional.of(new AnnouncementDocument(1L, "APPLYHOME",
                "재당첨제한 전매제한 거주의무기간 분양가상한제 택지유형\n없음 1년 없음 미적용 민간택지\n본문", Instant.now())));
        QaAnswer fromAi = new QaAnswer(Status.ANSWERED, "AI 답", List.of());
        when(qa.answer(1L, "신청 자격은?")).thenReturn(fromAi);
        NoticeQuestionService service = new NoticeQuestionService(queryService, docs, qa);

        QaAnswer fact = service.answer(1L, "전매제한 기간은?");
        assertThat(fact.status()).isEqualTo(Status.FACT);
        assertThat(fact.answer()).isEqualTo("전매제한: 1년");
        assertThat(fact.isFact()).isTrue();
        assertThat(fact.mentionsAmount()).isFalse();
        verify(qa, never()).answer(1L, "전매제한 기간은?");
        verify(qa).count("FACT");

        assertThat(service.answer(1L, "신청 자격은?")).isSameAs(fromAi);
    }
}
