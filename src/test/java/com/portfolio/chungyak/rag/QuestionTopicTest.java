package com.portfolio.chungyak.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.portfolio.chungyak.rag.QuestionTopic.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 질문 주제 분류 (#40) — 우선순위(서류 > 규제 > 금액 > 세대수 > 자격 > 일정 > 위치)가 바뀌면 깨진다 */
class QuestionTopicTest {

    @Test
    @DisplayName("겹치는 단어는 우선순위로 — 서류 제출 '기간'은 서류, 통장 가입'기간'은 자격, 잔금 '언제'는 금액")
    void priority() {
        assertThat(classify("서류 제출 기간은 언제예요?")).isEqualTo(DOCUMENTS);
        assertThat(classify("청약통장 가입기간 조건이 있나요?")).isEqualTo(ELIGIBILITY);
        assertThat(classify("잔금은 언제까지 내야 해요?")).isEqualTo(MONEY);
        assertThat(classify("전매제한 기간은?")).isEqualTo(REGULATION);
        assertThat(classify("신혼부부 특별공급은 몇 세대예요?")).isEqualTo(SUPPLY);
        assertThat(classify("당첨자 발표일은 언제예요?")).isEqualTo(SCHEDULE);
        assertThat(classify("견본주택 주소가 어디예요?")).isEqualTo(LOCATION);
        assertThat(classify("울산 사는데 신청할 수 있나요?")).isEqualTo(ELIGIBILITY);
        assertThat(classify("반려동물 키워도 되나요?")).isEqualTo(OTHER);
        assertThat(classify(null)).isEqualTo(OTHER);
    }

    @Test
    @DisplayName("버튼 문구는 모두 '기타'가 아닌 주제로 — 버튼 클릭이 기타로 쌓이면 3단계 근거가 흐려진다")
    void quickQuestionsHaveTopics() throws IOException {
        for (String q : java.util.List.of(QuickQuestions.WINNER, QuickQuestions.RECEPT, QuickQuestions.RESALE,
                QuickQuestions.RESIDE, QuickQuestions.PRICE, QuickQuestions.MOVE_IN, QuickQuestions.TOTAL,
                QuickQuestions.DEPOSIT, QuickQuestions.MODEL_HOUSE, QuickQuestions.QUALIFY,
                QuickQuestions.DOCS_WHEN, QuickQuestions.WAITLIST)) {
            assertThat(classify(q)).as(q).isNotEqualTo(OTHER);
        }
        // eval 질문 분포 — 참고 출력
        var dist = new java.util.TreeMap<String, Integer>();
        NoticeQaRetrievalEvalTest.readCases().forEach(c -> dist.merge(classify(c.question()).getLabel(), 1, Integer::sum));
        System.out.println("[eval 질문 주제 분포] " + dist);
    }
}
