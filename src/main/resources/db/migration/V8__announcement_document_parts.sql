-- 공고문 원문 전문 저장 (#29). raw_text(VARCHAR(16000))에 15,500자로 잘라 넣던 것을, 앞 15,000자는 raw_text 에
-- 두고 나머지를 15,000자씩 이 표에 나눠 담는다. 실측(2026-10-13, LH 8건): 전문 30,000~68,000자, 잘림 전 23~52% 만 저장됐다.
-- MariaDB / H2 MODE=MySQL 공용. utf8mb4 4B/자 → content 15000자 = 60,000B + 나머지 컬럼 20B < 행 상한 65,535B.
-- TEXT 는 Hibernate validate 가 VARCHAR 를 기대해 막으므로 VARCHAR 를 유지한다(CLAUDE.md).

CREATE TABLE announcement_document_part (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    document_id BIGINT         NOT NULL,
    part_index  INT            NOT NULL,
    content     VARCHAR(15000) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_announcement_document_part UNIQUE (document_id, part_index),
    CONSTRAINT fk_announcement_document_part_doc FOREIGN KEY (document_id) REFERENCES announcement_document (id)
);
