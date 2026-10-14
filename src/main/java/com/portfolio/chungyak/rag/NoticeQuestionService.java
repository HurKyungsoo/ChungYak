package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.rag.DocumentQaService.QaAnswer;
import com.portfolio.chungyak.repository.AnnouncementDocumentRepository;
import com.portfolio.chungyak.service.AnnouncementQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * "이 공고에 물어보기"의 입구 (#39) — <b>결정론 답변을 먼저</b>, 아니면 AI 공고문 Q&A.
 *
 * 답이 구조화 데이터(청약홈·LH API, 공고문 요약표)에 있는 질문은 {@link FactAnswerer} 가 그 값으로 바로 답한다 —
 * LLM 을 부르지 않으니 틀릴 여지가 없고 무료 한도도 쓰지 않는다. 그다음 공고문 표 값(계약금 비율·임대료·소득 기준)은
 * {@link TableFactAnswerer} 가 표 행을 그대로 보여 준다(#42). 나머지는 {@link DocumentQaService}(검색 + LLM + 근거 검사).
 */
@Slf4j
@Service
public class NoticeQuestionService {

    private final AnnouncementQueryService queryService;
    private final AnnouncementDocumentRepository documentRepository;
    private final DocumentQaService qaService;
    /** 주제별 집계(#40) — 질문 원문은 넘기지 않고 주제·결과만 쌓는다 */
    private final Optional<QaTopicStats> topicStats;

    public NoticeQuestionService(AnnouncementQueryService queryService,
                                 AnnouncementDocumentRepository documentRepository,
                                 DocumentQaService qaService) {
        this(queryService, documentRepository, qaService, Optional.empty());
    }

    @Autowired
    public NoticeQuestionService(AnnouncementQueryService queryService,
                                 AnnouncementDocumentRepository documentRepository,
                                 DocumentQaService qaService,
                                 Optional<QaTopicStats> topicStats) {
        this.queryService = queryService;
        this.documentRepository = documentRepository;
        this.qaService = qaService;
        this.topicStats = topicStats;
    }

    public QaAnswer answer(Long announcementId, String question) {
        return answer(announcementId, question, false);
    }

    /** @param viaQuick 자주 묻는 질문 버튼으로 왔다고 화면이 알림(#40) — 문구가 실제 버튼 목록에 있을 때만 버튼으로 센다 */
    public QaAnswer answer(Long announcementId, String question, boolean viaQuick) {
        if (!qaService.isEnabled() || question == null || question.isBlank()) {
            return qaService.answer(announcementId, question);
        }
        Optional<Announcement> announcement = queryService.findDetail(announcementId);
        var document = documentRepository.findByAnnouncementId(announcementId);
        Optional<NoticeSummaryTable> table = document.flatMap(d -> NoticeSummaryTable.parse(d.getFirstPart()));
        // 버튼 문구 확인 — via=quick 만 믿고 저장하면 아무 글이나 집계 표에 들어간다(원문 저장 금지)
        String quick = viaQuick ? announcement
                .flatMap(a -> QuickQuestions.of(a, table).stream()
                        .map(QuickQuestions.QuickQuestion::text).filter(question::equals).findFirst())
                .orElse(null) : null;
        Optional<FactAnswerer.Fact> fact = announcement.flatMap(a -> FactAnswerer.answer(a, table, question));
        if (fact.isEmpty()) fact = document.flatMap(d -> TableFactAnswerer.answer(d.getRawText(), question));
        QaAnswer result;
        if (fact.isPresent()) {
            log.info("공고 #{} Q&A — 구조화 데이터로 답함({})", announcementId, fact.get().source());
            qaService.count("FACT");
            result = QaAnswer.fact(fact.get().text(), fact.get().source());
        } else {
            result = qaService.answer(announcementId, question);
        }
        topicStats.ifPresent(st -> st.record(question, result.status(), quick));
        return result;
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
