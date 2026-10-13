package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.rag.DocumentQaService.QaAnswer;
import com.portfolio.chungyak.repository.AnnouncementDocumentRepository;
import com.portfolio.chungyak.service.AnnouncementQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * "이 공고에 물어보기"의 입구 (#39) — <b>결정론 답변을 먼저</b>, 아니면 AI 공고문 Q&A.
 *
 * 답이 구조화 데이터(청약홈·LH API, 공고문 요약표)에 있는 질문은 {@link FactAnswerer} 가 그 값으로 바로 답한다 —
 * LLM 을 부르지 않으니 틀릴 여지가 없고 무료 한도도 쓰지 않는다. 나머지는 {@link DocumentQaService}(검색 + LLM + 근거 검사).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeQuestionService {

    private final AnnouncementQueryService queryService;
    private final AnnouncementDocumentRepository documentRepository;
    private final DocumentQaService qaService;

    public QaAnswer answer(Long announcementId, String question) {
        if (!qaService.isEnabled() || question == null || question.isBlank()) {
            return qaService.answer(announcementId, question);
        }
        Optional<Announcement> announcement = queryService.findDetail(announcementId);
        Optional<NoticeSummaryTable> table = documentRepository.findByAnnouncementId(announcementId)
                .flatMap(d -> NoticeSummaryTable.parse(d.getFirstPart()));
        Optional<FactAnswerer.Fact> fact = announcement.flatMap(a -> FactAnswerer.answer(a, table, question));
        if (fact.isPresent()) {
            log.info("공고 #{} Q&A — 구조화 데이터로 답함({})", announcementId, fact.get().source());
            qaService.count("FACT");
            return QaAnswer.fact(fact.get().text(), fact.get().source());
        }
        return qaService.answer(announcementId, question);
    }
}
