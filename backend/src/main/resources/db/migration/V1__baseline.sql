-- 校园失物招领系统 v1 基线表
-- 表名用 app_user 而非 user，避免与 MySQL 保留命名空间冲突。

CREATE TABLE app_user (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    student_no     VARCHAR(32)  NOT NULL,
    nickname       VARCHAR(64)  NOT NULL,
    department     VARCHAR(128) NULL,
    email          VARCHAR(128) NULL,
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(16)  NOT NULL DEFAULT 'USER',
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    contact_type   VARCHAR(16)  NULL,
    contact_value  VARCHAR(128) NULL,
    contact_public TINYINT(1)   NOT NULL DEFAULT 0,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_app_user_student_no (student_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE report (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    type            VARCHAR(8)   NOT NULL,
    title           VARCHAR(128) NOT NULL,
    category        VARCHAR(32)  NOT NULL,
    location        VARCHAR(32)  NOT NULL,
    location_detail VARCHAR(255) NULL,
    event_time      DATETIME(6)  NULL,
    description     TEXT         NULL,
    status          VARCHAR(24)  NOT NULL DEFAULT 'OPEN',
    publisher_id    BIGINT       NOT NULL,
    view_count      INT          NOT NULL DEFAULT 0,
    pinned          TINYINT(1)   NOT NULL DEFAULT 0,
    custody         VARCHAR(16)  NULL,
    handover_no     VARCHAR(64)  NULL,
    reviewed_by     BIGINT       NULL,
    reviewed_at     DATETIME(6)  NULL,
    reject_reason   VARCHAR(255) NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_report_status_created (status, created_at),
    KEY idx_report_type_category (type, category),
    KEY idx_report_publisher (publisher_id),
    CONSTRAINT fk_report_publisher FOREIGN KEY (publisher_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE report_image (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    report_id     BIGINT       NOT NULL,
    path          VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    mime          VARCHAR(64)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    sort_order    INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_report_image_report (report_id, sort_order),
    CONSTRAINT fk_report_image_report FOREIGN KEY (report_id) REFERENCES report (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE claim (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    report_id     BIGINT       NOT NULL,
    applicant_id  BIGINT       NOT NULL,
    direction     VARCHAR(16)  NOT NULL,
    feature_desc  TEXT         NOT NULL,
    expect_time   DATETIME(6)  NULL,
    expect_place  VARCHAR(255) NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    handled_by    BIGINT       NULL,
    handled_at    DATETIME(6)  NULL,
    reject_reason VARCHAR(255) NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_claim_report_status (report_id, status),
    KEY idx_claim_applicant_status (applicant_id, status),
    CONSTRAINT fk_claim_report FOREIGN KEY (report_id) REFERENCES report (id) ON DELETE CASCADE,
    CONSTRAINT fk_claim_applicant FOREIGN KEY (applicant_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE chat_session (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    claim_id     BIGINT      NOT NULL,
    report_id    BIGINT      NOT NULL,
    owner_id     BIGINT      NOT NULL,
    applicant_id BIGINT      NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at   DATETIME(6) NOT NULL,
    closed_at    DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_chat_session_claim (claim_id),
    KEY idx_chat_session_owner (owner_id),
    KEY idx_chat_session_applicant (applicant_id),
    CONSTRAINT fk_chat_session_claim FOREIGN KEY (claim_id) REFERENCES claim (id),
    CONSTRAINT fk_chat_session_report FOREIGN KEY (report_id) REFERENCES report (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE message (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    session_id BIGINT      NOT NULL,
    sender_id  BIGINT      NOT NULL,
    body       TEXT        NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_message_session (session_id, created_at),
    CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES chat_session (id) ON DELETE CASCADE,
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE notification (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT      NOT NULL,
    type         VARCHAR(32) NOT NULL,
    target_type  VARCHAR(16) NULL,
    target_id    BIGINT      NULL,
    read_flag    TINYINT(1)  NOT NULL DEFAULT 0,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notification_recipient (recipient_id, read_flag, created_at),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE report_action (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    operator_id BIGINT       NOT NULL,
    action      VARCHAR(16)  NOT NULL,
    report_id   BIGINT       NOT NULL,
    reason      VARCHAR(255) NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_report_action_report (report_id, created_at),
    CONSTRAINT fk_report_action_operator FOREIGN KEY (operator_id) REFERENCES app_user (id),
    CONSTRAINT fk_report_action_report FOREIGN KEY (report_id) REFERENCES report (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE report_flag (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    reporter_id   BIGINT       NOT NULL,
    target_type   VARCHAR(16)  NOT NULL,
    target_id     BIGINT       NOT NULL,
    reason        VARCHAR(32)  NOT NULL,
    detail        VARCHAR(255) NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    handled_by    BIGINT       NULL,
    handled_at    DATETIME(6)  NULL,
    handle_result VARCHAR(32)  NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_report_flag_status (status, created_at),
    KEY idx_report_flag_target (target_type, target_id),
    CONSTRAINT fk_report_flag_reporter FOREIGN KEY (reporter_id) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
