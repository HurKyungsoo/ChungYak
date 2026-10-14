package com.portfolio.chungyak.repository;

import com.portfolio.chungyak.domain.QaTopicStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface QaTopicStatRepository extends JpaRepository<QaTopicStat, Long> {

    /** 한 칸 +1 — 동시 요청에도 잃지 않게 DB 가 원자적으로 올린다(MariaDB·H2 MODE=MySQL 공용 구문) */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            INSERT INTO qa_topic_stat (stat_date, topic, outcome, quick_question, cnt)
            VALUES (:statDate, :topic, :outcome, :quickQuestion, 1)
            ON DUPLICATE KEY UPDATE cnt = cnt + 1""")
    void increment(@Param("statDate") LocalDate statDate, @Param("topic") String topic,
                   @Param("outcome") String outcome, @Param("quickQuestion") String quickQuestion);

    List<QaTopicStat> findByStatDateGreaterThanEqual(LocalDate from);
}
