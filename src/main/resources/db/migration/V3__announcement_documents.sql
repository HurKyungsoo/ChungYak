-- 공고문 RAG: 원문 저장 + 임베딩 청크.
-- MariaDB(운영) / H2 MODE=MySQL(로컬·테스트) 공용.
-- 벡터 검색은 앱에서 코사인으로 한다(공고 수백 건 규모) — 임베딩은 JSON 배열 문자열로 보관.
-- 큰 텍스트도 VARCHAR 로 둔다: MariaDB VARCHAR 상한(65,535바이트) 안이고 H2·양쪽에서 validate 통과.

CREATE TABLE announcement_document (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    announcement_id BIGINT         NOT NULL,
    source          VARCHAR(20)    NOT NULL,
    raw_text        VARCHAR(20000) NOT NULL,
    text_hash       VARCHAR(64)    NOT NULL,
    fetched_at      TIMESTAMP      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_announcement_document UNIQUE (announcement_id),
    CONSTRAINT fk_announcement_document_ann FOREIGN KEY (announcement_id) REFERENCES announcement (id)
);

CREATE TABLE document_chunk (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    announcement_id  BIGINT         NOT NULL,
    chunk_index      INT            NOT NULL,
    content          VARCHAR(2000)  NOT NULL,
    embedding        VARCHAR(20000) NOT NULL,   -- float[] 를 JSON 배열로 직렬화
    embedding_model  VARCHAR(40)    NOT NULL,
    source_text_hash VARCHAR(64)    NOT NULL,   -- 원문이 바뀌면 재인덱싱
    created_at       TIMESTAMP      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_document_chunk UNIQUE (announcement_id, chunk_index),
    CONSTRAINT fk_document_chunk_ann FOREIGN KEY (announcement_id) REFERENCES announcement (id)
);

CREATE INDEX idx_document_chunk_ann ON document_chunk (announcement_id);
