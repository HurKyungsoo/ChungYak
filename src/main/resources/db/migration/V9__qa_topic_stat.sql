-- 공고문 Q&A 주제별 집계 (#40 2단계). 질문 원문은 저장하지 않는다 — 소득·가족 등 개인정보가 섞일 수 있다.
-- 하루 × 주제(일정·금액 …) × 결과(FACT·ANSWERED·NO_MATCH …) × 자주 묻는 질문 버튼 문구별 개수만 쌓는다.
-- quick_question 은 서버가 만든 버튼 문구만 들어간다(직접 입력한 질문은 '') — 사용자 글이 아니다.
-- MariaDB / H2 MODE=MySQL 공용. utf8mb4 4B/자 → 60자 240B + 나머지 ~50B, 행 상한·유니크 인덱스 상한(3,072B) 안.
CREATE TABLE qa_topic_stat (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    stat_date      DATE        NOT NULL,
    topic          VARCHAR(20) NOT NULL,
    outcome        VARCHAR(20) NOT NULL,
    quick_question VARCHAR(60) NOT NULL DEFAULT '',
    cnt            INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_qa_topic_stat UNIQUE (stat_date, topic, outcome, quick_question)
);
