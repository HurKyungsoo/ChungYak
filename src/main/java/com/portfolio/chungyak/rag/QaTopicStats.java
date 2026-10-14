package com.portfolio.chungyak.rag;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.portfolio.chungyak.domain.QaTopicStat;
import com.portfolio.chungyak.rag.DocumentQaService.Status;
import com.portfolio.chungyak.repository.QaTopicStatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 공고문 Q&A 주제별 집계 (#40 2·3단계) — <b>질문 원문은 저장하지 않는다.</b> 주제 × 결과 × 버튼 문구 × 날짜 개수만.
 * 집계 실패는 답변을 막지 않는다(로그만).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaTopicStats {

    /** 버튼 정렬(3단계)에 쓰는 기간 */
    static final int CLICK_WINDOW_DAYS = 30;

    private final QaTopicStatRepository repository;
    private final Clock clock;

    /** 버튼 클릭 수 캐시 — 상세 화면을 열 때마다 집계 쿼리를 돌리지 않는다 */
    private final Cache<String, Map<String, Integer>> clickCache = Caffeine.newBuilder()
            .maximumSize(1).expireAfterWrite(Duration.ofMinutes(10)).build();

    /**
     * @param quickQuestion 서버가 만든 버튼 문구일 때만(호출부가 확인), 직접 입력이면 null — 이때 질문은 주제 분류에만 쓰고 버린다
     */
    public void record(String question, Status status, String quickQuestion) {
        if (status == null || status == Status.DISABLED) return;
        try {
            repository.increment(LocalDate.now(clock), QuestionTopic.classify(question).name(), status.name(),
                    quickQuestion == null ? "" : quickQuestion);
        } catch (RuntimeException e) {
            log.warn("Q&A 주제 집계 실패 — 답변에는 영향 없음: {}", e.toString());
        }
    }

    /** 최근 30일 버튼별 클릭 수 (버튼 문구 → 횟수) */
    public Map<String, Integer> quickClicks() {
        return clickCache.get("clicks", k -> {
            Map<String, Integer> out = new java.util.HashMap<>();
            for (QaTopicStat s : since(CLICK_WINDOW_DAYS)) {
                if (!s.getQuickQuestion().isEmpty()) out.merge(s.getQuickQuestion(), s.getCnt(), Integer::sum);
            }
            return out;
        });
    }

    /** 버튼을 많이 눌린 순으로(같으면 원래 순서 — 공식 데이터 먼저) */
    public List<QuickQuestions.QuickQuestion> sortByClicks(List<QuickQuestions.QuickQuestion> questions) {
        Map<String, Integer> clicks;
        try {
            clicks = quickClicks();
        } catch (RuntimeException e) {
            log.warn("버튼 클릭 수 조회 실패 — 기본 순서로: {}", e.toString());
            return questions;
        }
        return questions.stream()
                .sorted(Comparator.comparingInt((QuickQuestions.QuickQuestion q) -> clicks.getOrDefault(q.text(), 0)).reversed())
                .toList();
    }

    /**
     * 관리자 조회 — 주제 순위(많은 순)와 주제별 결과 분포·못 찾음 비율, 버튼별 클릭 수.
     * 못 찾음 = NO_MATCH + UNGROUNDED + ANSWER_FAILED (사용자가 답을 못 본 경우).
     */
    public Map<String, Object> summary(int days) {
        List<QaTopicStat> rows = since(days);
        Map<QuestionTopic, Map<String, Integer>> byTopic = new EnumMap<>(QuestionTopic.class);
        Map<String, Integer> clicks = new TreeMap<>();
        for (QaTopicStat s : rows) {
            QuestionTopic t;
            try {
                t = QuestionTopic.valueOf(s.getTopic());
            } catch (IllegalArgumentException e) {
                t = QuestionTopic.OTHER;
            }
            byTopic.computeIfAbsent(t, k -> new TreeMap<>()).merge(s.getOutcome(), s.getCnt(), Integer::sum);
            if (!s.getQuickQuestion().isEmpty()) clicks.merge(s.getQuickQuestion(), s.getCnt(), Integer::sum);
        }
        List<Map<String, Object>> topics = byTopic.entrySet().stream()
                .map(e -> {
                    int total = e.getValue().values().stream().mapToInt(Integer::intValue).sum();
                    int missed = e.getValue().getOrDefault(Status.NO_MATCH.name(), 0)
                            + e.getValue().getOrDefault(Status.UNGROUNDED.name(), 0)
                            + e.getValue().getOrDefault(Status.ANSWER_FAILED.name(), 0);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("topic", e.getKey().getLabel());
                    m.put("total", total);
                    m.put("missed", missed);
                    m.put("missedRate", total == 0 ? 0 : Math.round(missed * 1000.0 / total) / 10.0);
                    m.put("outcomes", e.getValue());
                    return m;
                })
                .sorted(Comparator.comparingInt((Map<String, Object> m) -> (int) m.get("total")).reversed())
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("from", LocalDate.now(clock).minusDays(days - 1L).toString());
        out.put("topics", topics);
        out.put("quickClicks", clicks.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> Map.of("question", e.getKey(), "count", e.getValue())).toList());
        return out;
    }

    private List<QaTopicStat> since(int days) {
        return repository.findByStatDateGreaterThanEqual(LocalDate.now(clock).minusDays(Math.max(1, days) - 1L));
    }
}
