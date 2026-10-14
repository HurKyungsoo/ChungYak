package com.portfolio.chungyak.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 공고문 Q&A 주제별 집계 한 칸 (#40) — 날짜 × 주제 × 결과 × 버튼 문구의 질문 개수. <b>질문 원문은 담지 않는다.</b>
 * 쓰기는 {@code QaTopicStatRepository#increment}(원자적 upsert)로만 한다.
 */
@Entity
@Table(name = "qa_topic_stat",
        uniqueConstraints = @UniqueConstraint(name = "uk_qa_topic_stat",
                columnNames = {"stat_date", "topic", "outcome", "quick_question"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QaTopicStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(nullable = false, length = 20)
    private String topic;

    @Column(nullable = false, length = 20)
    private String outcome;

    /** 자주 묻는 질문 버튼 문구(서버가 만든 문장). 직접 입력한 질문은 빈 문자열 */
    @Column(name = "quick_question", nullable = false, length = 60)
    private String quickQuestion;

    @Column(nullable = false)
    private int cnt;
}
