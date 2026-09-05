-- "새 공고 알림"(C2) 구독. 로그인 없이 이메일 하나로 판정 조건(자격 판정 폼과 동일, 소득·자산
-- 포함)을 저장해 두고, 새 공고가 들어올 때마다 배치가 EligibilityEngine 으로 다시 판정해
-- 실제 자격이 되면 메일을 보낸다. status=PENDING 은 이메일 확인 전이라 배치 대상이 아니다.
CREATE TABLE alert_subscription (
    id                             BIGINT       NOT NULL AUTO_INCREMENT,
    email                          VARCHAR(254) NOT NULL,
    reference_announcement_id      BIGINT       NOT NULL,
    status                         VARCHAR(20)  NOT NULL,
    confirm_token                  VARCHAR(36)  NOT NULL,
    unsubscribe_token              VARCHAR(36)  NOT NULL,
    created_at                     TIMESTAMP    NOT NULL,
    -- 자격 판정 폼과 같은 조건
    married                        BOOLEAN      NOT NULL DEFAULT FALSE,
    months_since_marriage          INT,
    child_count                    INT          NOT NULL DEFAULT 0,
    has_newborn                    BOOLEAN      NOT NULL DEFAULT FALSE,
    has_child_under_six            BOOLEAN      NOT NULL DEFAULT FALSE,
    houseless                      BOOLEAN      NOT NULL DEFAULT FALSE,
    account_months                 INT,
    account_payment_count          INT,
    account_deposit                INT,
    ever_owned_house               BOOLEAN      NOT NULL DEFAULT FALSE,
    supporting_old_parents         BOOLEAN      NOT NULL DEFAULT FALSE,
    household_head                 BOOLEAN      NOT NULL DEFAULT FALSE,
    residence_months_in_region     INT,
    monthly_household_income       INT,
    household_size                 INT,
    dual_income                    BOOLEAN      NOT NULL DEFAULT FALSE,
    total_assets                   BIGINT,
    car_value                      INT,
    ever_won_special_supply        BOOLEAN      NOT NULL DEFAULT FALSE,
    months_since_last_win          INT,
    past_win_in_speculation_area   BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uk_alert_confirm_token UNIQUE (confirm_token),
    CONSTRAINT uk_alert_unsubscribe_token UNIQUE (unsubscribe_token)
);

CREATE INDEX idx_alert_subscription_status ON alert_subscription (status);
