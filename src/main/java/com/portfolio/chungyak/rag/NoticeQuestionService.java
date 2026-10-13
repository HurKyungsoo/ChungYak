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

    /** AI 답이 "확인할 수 없다"는 취지인지 — 이때도 다시 묻기를 권한다 */
    private static final java.util.regex.Pattern NOT_FOUND =
            java.util.regex.Pattern.compile("확인할 수 없|확인되지 않|찾을 수 없|나와 있지 않");

    /**
     * 답을 못 찾았을 때 "공고문 용어로 다시 묻기" 질문 (#39) — 질문의 일상어("모델하우스")를 공고문 용어("견본주택")로
     * 바꾼 문장. 결정론(사전)이다. 답을 찾았거나 바꿀 말이 없으면 empty.
     */
    public Optional<String> rephraseSuggestion(String question, QaAnswer qa) {
        if (qa == null || question == null || qa.isFact() || !qa.isShown()) return Optional.empty();
        boolean found = qa.hasAnswer() && !NOT_FOUND.matcher(qa.answer()).find();
        return found ? Optional.empty() : QuerySynonyms.rephrase(question);
    }
}
