-- 청약홈 특별공급 신청현황 기록 — 판정 결과에서 "어느 유형으로 넣을지" 참고용으로 같은 지역의 과거 유형별 경쟁을 보여 주려고 쌓는다.
-- ApplyhomeInfoCmpetRtSvc/v1 getAPTSpsplyReqstStus(주택형 × 유형). 신청 건수는 해당지역·시도·기타지역 합.
-- 판정과 무관하다(규칙 엔진은 이 표를 보지 않는다). 지역은 API 에 없어 announcement 와 house_manage_no 로 조인한다.
-- MariaDB / H2 MODE=MySQL 공용. 행이 작다(VARCHAR 합계 70자 — utf8mb4 4B 로도 행 상한과 거리가 멀다).

CREATE TABLE special_supply_history (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    house_manage_no VARCHAR(20)  NOT NULL,
    house_type      VARCHAR(30)  NOT NULL,
    supply_type     VARCHAR(20)  NOT NULL,   -- SpecialSupplyType 이름 (NEWLYWED, FIRST_TIME …)
    households      INT          NOT NULL,   -- 배정 세대
    applications    INT          NOT NULL,   -- 신청 건수(해당·시도·기타지역 합)
    PRIMARY KEY (id)
);
CREATE INDEX idx_special_supply_history_house ON special_supply_history (house_manage_no);
