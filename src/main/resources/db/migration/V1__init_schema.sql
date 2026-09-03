-- 청약나침반 초기 스키마.
-- MariaDB(운영) / H2 MODE=MySQL(로컬·테스트) 양쪽에서 동작하도록 작성.
-- 엔티티 변경 시 새 V{n}__ 파일을 추가한다 (이 파일은 수정 금지 — Flyway 체크섬).

CREATE TABLE announcement (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    external_id                VARCHAR(100) NOT NULL,
    house_manage_no           VARCHAR(20)  NOT NULL,
    pblanc_no                 VARCHAR(20)  NOT NULL,
    house_name                VARCHAR(300) NOT NULL,
    house_type                VARCHAR(30)  NOT NULL,
    house_detail_type         VARCHAR(20)  NOT NULL,
    region_name               VARCHAR(50),
    region_code               VARCHAR(20),
    address                   VARCHAR(500),
    zip_code                  VARCHAR(10),
    total_supply_count        INT,
    notice_date               DATE,
    recept_begin_date         DATE,
    recept_end_date           DATE,
    special_recept_begin_date DATE,
    special_recept_end_date   DATE,
    winner_announce_date      DATE,
    -- RegulationFlags (@Embedded)
    speculation_overheated    BOOLEAN      NOT NULL DEFAULT FALSE,
    adjustment_target         BOOLEAN      NOT NULL DEFAULT FALSE,
    price_cap_applied         BOOLEAN      NOT NULL DEFAULT FALSE,
    redevelopment             BOOLEAN      NOT NULL DEFAULT FALSE,
    public_housing_district   BOOLEAN      NOT NULL DEFAULT FALSE,
    large_scale_development    BOOLEAN      NOT NULL DEFAULT FALSE,
    public_housing_special_law BOOLEAN     NOT NULL DEFAULT FALSE,
    notice_url                VARCHAR(500),
    homepage_url              VARCHAR(300),
    inquiry_tel               VARCHAR(50),
    developer_name            VARCHAR(200),
    constructor_name          VARCHAR(300),
    move_in_year_month        VARCHAR(10),
    PRIMARY KEY (id),
    CONSTRAINT uk_announcement_external UNIQUE (external_id)
);

CREATE INDEX idx_announcement_recept ON announcement (recept_begin_date, recept_end_date);
CREATE INDEX idx_announcement_region ON announcement (region_name);

CREATE TABLE unit_type (
    id                     BIGINT      NOT NULL AUTO_INCREMENT,
    announcement_id        BIGINT      NOT NULL,
    model_no               VARCHAR(10) NOT NULL,
    type_name              VARCHAR(50) NOT NULL,
    supply_area            VARCHAR(30),
    general_supply_count   INT         NOT NULL DEFAULT 0,
    special_supply_count   INT         NOT NULL DEFAULT 0,
    -- SupplyBreakdown (@Embedded)
    multi_child            INT         NOT NULL DEFAULT 0,
    newlywed               INT         NOT NULL DEFAULT 0,
    first_time             INT         NOT NULL DEFAULT 0,
    old_parents            INT         NOT NULL DEFAULT 0,
    institution_recommend  INT         NOT NULL DEFAULT 0,
    youth                  INT         NOT NULL DEFAULT 0,
    newborn                INT         NOT NULL DEFAULT 0,
    transfer_institution   INT         NOT NULL DEFAULT 0,
    etc                    INT         NOT NULL DEFAULT 0,
    top_amount             INT,
    PRIMARY KEY (id),
    CONSTRAINT uk_unit_type UNIQUE (announcement_id, model_no),
    CONSTRAINT fk_unit_type_announcement FOREIGN KEY (announcement_id) REFERENCES announcement (id)
);

CREATE INDEX idx_unit_type_announcement ON unit_type (announcement_id);
