package com.portfolio.chungyak.repository;

import com.portfolio.chungyak.rag.DocumentQaService.Status;
import com.portfolio.chungyak.rag.QaTopicStats;
import com.portfolio.chungyak.rag.QuickQuestions.QuickQuestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 공고문 Q&A 주제별 집계 (#40) — V9 마이그레이션 + 원자적 upsert + 관리자 요약 + 버튼 정렬 */
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:qa-topic-test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QaTopicStatRepositoryTest {

    @Autowired private QaTopicStatRepository repository;

    private QaTopicStats stats(String day) {
        return new QaTopicStats(repository, Clock.fixed(Instant.parse(day + "T03:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    @Test
    @DisplayName("같은 칸은 +1, 원문은 남지 않는다 — 주제·결과·버튼 문구만, 요약은 주제 많은 순·못 찾음 비율")
    void recordAndSummarize() {
        repository.deleteAll();
        QaTopicStats today = stats("2026-10-14");
        today.record("우리 부부 소득 900만원인데 계약금 몇 퍼센트?", Status.ANSWERED, null);
        today.record("계약금은 몇 퍼센트예요?", Status.NO_MATCH, "계약금은 몇 퍼센트예요?");
        today.record("계약금은 몇 퍼센트예요?", Status.NO_MATCH, "계약금은 몇 퍼센트예요?");
        today.record("당첨자 발표일은 언제예요?", Status.FACT, "당첨자 발표일은 언제예요?");
        today.record("아무거나", Status.DISABLED, null);   // 기능 꺼짐은 세지 않는다
        stats("2026-09-01").record("견본주택 어디", Status.ANSWERED, null);   // 기간 밖

        assertThat(repository.findAll()).hasSize(4)
                .allSatisfy(r -> assertThat(r.getQuickQuestion()).doesNotContain("소득", "900"));

        Map<String, Object> summary = today.summary(30);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topics = (List<Map<String, Object>>) summary.get("topics");
        assertThat(topics).hasSize(2);
        assertThat(topics.get(0)).containsEntry("topic", "금액").containsEntry("total", 3)
                .containsEntry("missed", 2).containsEntry("missedRate", 66.7);
        assertThat(topics.get(1)).containsEntry("topic", "일정").containsEntry("total", 1);
        assertThat(summary.get("quickClicks").toString()).startsWith("[{").contains("count=2");

        // 3단계 — 많이 눌린 버튼이 앞으로, 같으면 원래 순서
        List<QuickQuestion> sorted = today.sortByClicks(List.of(
                new QuickQuestion("청약 접수 일정은 언제예요?", true),
                new QuickQuestion("당첨자 발표일은 언제예요?", true),
                new QuickQuestion("계약금은 몇 퍼센트예요?", false)));
        assertThat(sorted).extracting(QuickQuestion::text).containsExactly(
                "계약금은 몇 퍼센트예요?", "당첨자 발표일은 언제예요?", "청약 접수 일정은 언제예요?");
    }
}
