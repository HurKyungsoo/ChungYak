-- 청약홈 당첨가점·경쟁률 기록 (#34) — 접수 예정 공고에 "이 지역 최근 당첨 기록"을 참고로 보여 주려고 쌓는다.
-- ApplyhomeInfoCmpetRtSvc/v1 의 getAPTLttotPblancScore(주택형 × 거주구분) · getAPTLttotPblancCmpet(주택형 × 순위 × 거주구분).
-- 판정과 무관하다(규칙 엔진은 이 표를 보지 않는다). 지역은 API 에 없어 announcement 와 house_manage_no 로 조인한다.
-- MariaDB / H2 MODE=MySQL 공용. 행이 작다(VARCHAR 합계 < 100자).

CREATE TABLE competition_score (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    house_manage_no VARCHAR(20)  NOT NULL,
    model_no        VARCHAR(10)  NOT NULL,
    house_type      VARCHAR(30)  NOT NULL,
    reside_code     VARCHAR(4)   NOT NULL,   -- 01 해당지역 · 02 기타지역 · 03 기타경기
    lowest          INT          NOT NULL,   -- 0 = 추첨·미달·미집계(전체의 ~20%) — 집계에서 뺀다
    average         INT          NOT NULL,
    highest         INT          NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_competition_score_house ON competition_score (house_manage_no);

CREATE TABLE competition_rate (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    house_manage_no VARCHAR(20)  NOT NULL,
    model_no        VARCHAR(10)  NOT NULL,
    house_type      VARCHAR(30)  NOT NULL,
    rank_code       INT          NOT NULL,   -- 1순위 · 2순위
    reside_code     VARCHAR(4)   NOT NULL,
    supply          INT          NOT NULL,
    requests        INT,                     -- 신청 건수(문자열로 온다 — 숫자가 아니면 NULL)
    rate_text       VARCHAR(20)  NOT NULL,   -- 원문 그대로: "10.17", "(△1)" 미달, "-" 미집계
    PRIMARY KEY (id)
);
CREATE INDEX idx_competition_rate_house ON competition_rate (house_manage_no);
