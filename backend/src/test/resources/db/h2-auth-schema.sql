-- H2 stand-in for migration V003 (Oracle), used only by the JDBC adapter tests. Same tables and columns;
-- RAW(16) becomes VARBINARY(16) and VARCHAR2(n CHAR) becomes VARCHAR(n). The real migration runs in FlywayMigrationIT.
CREATE SCHEMA IF NOT EXISTS azs_auth;

CREATE TABLE azs_auth.app_user (
    id                 VARBINARY(16)  NOT NULL,
    account_name       VARCHAR(100)   NOT NULL,
    prsn_code          VARCHAR(50),
    personel_user_name VARCHAR(100),
    branch_code        VARCHAR(10)    NOT NULL,
    branch_name        VARCHAR(200),
    job_position       VARCHAR(200),
    job                VARCHAR(200),
    role               VARCHAR(16)    NOT NULL,
    last_login_at      TIMESTAMP(6)   NOT NULL,
    created_at         TIMESTAMP(6)   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP(6)   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100),
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT ux_app_user_account_name UNIQUE (account_name)
    -- ck_app_user_role (role IN ('BRANCH', 'ADMIN')) exists in V003; H2 cannot evaluate a CHECK on a column named role
);

CREATE TABLE azs_auth.refresh_tokens (
    id           VARBINARY(16)  NOT NULL,
    family_id    VARBINARY(16)  NOT NULL,
    subject_id   VARBINARY(16)  NOT NULL,
    subject_name VARCHAR(100)   NOT NULL,
    role         VARCHAR(16)    NOT NULL,
    branch_code  VARCHAR(10),
    token_hash   VARCHAR(64)    NOT NULL,
    expires_at   TIMESTAMP(6)   NOT NULL,
    used_at      TIMESTAMP(6),
    revoked_at   TIMESTAMP(6),
    created_at   TIMESTAMP(6)   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT ux_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_tokens_family ON azs_auth.refresh_tokens (family_id);

CREATE TABLE azs_auth.yaghut_session (
    family_id   VARBINARY(16) NOT NULL,
    jsession_id VARCHAR(200)  NOT NULL,
    expires_at  TIMESTAMP(6),
    created_at  TIMESTAMP(6)  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_yaghut_session PRIMARY KEY (family_id)
);

CREATE TABLE azs_auth.otp_challenge (
    id            VARBINARY(16) NOT NULL,
    national_code VARCHAR(10)   NOT NULL,
    mobile        VARCHAR(20),
    otp_id        VARCHAR(100)  NOT NULL,
    family_id     VARBINARY(16) NOT NULL,
    sent_at       TIMESTAMP(6)  NOT NULL,
    status        VARCHAR(10)   DEFAULT 'SENT' NOT NULL,
    CONSTRAINT pk_otp_challenge PRIMARY KEY (id)
);

CREATE TABLE azs_auth.otp_lockout (
    national_code  VARCHAR(10)  NOT NULL,
    wrong_attempts NUMBER(3)    DEFAULT 0 NOT NULL,
    locked_until   TIMESTAMP(6),
    CONSTRAINT pk_otp_lockout PRIMARY KEY (national_code)
);

CREATE SCHEMA IF NOT EXISTS azs_inquiry;
CREATE TABLE azs_inquiry.inquiry_run (
    id                VARBINARY(16) NOT NULL,
    request_id        VARBINARY(16) NOT NULL,
    status            VARCHAR(10)   NOT NULL,
    decision          VARCHAR(10),
    active_request_id VARBINARY(16),
    started_at        TIMESTAMP(6)  NOT NULL,
    finished_at       TIMESTAMP(6),
    CONSTRAINT pk_inquiry_run PRIMARY KEY (id),
    CONSTRAINT ux_inquiry_run_active UNIQUE (active_request_id)
);
CREATE TABLE azs_inquiry.inquiry_result (
    run_id       VARBINARY(16) NOT NULL,
    inquiry_type VARCHAR(20)   NOT NULL,
    result       VARCHAR(10)   NOT NULL,
    failure_kind VARCHAR(15),
    message_key  VARCHAR(100),
    gender       VARCHAR(10),
    birth_date   DATE,
    finished_at  TIMESTAMP(6)  NOT NULL,
    CONSTRAINT pk_inquiry_result PRIMARY KEY (run_id, inquiry_type)
);

-- stand-in for AZS_BUSINESS.agency (V002), only what the login reads
CREATE SCHEMA IF NOT EXISTS azs_business;
CREATE TABLE azs_business.agency (
    id     VARBINARY(16) NOT NULL,
    cif    VARCHAR(20)   NOT NULL,
    code   VARCHAR(4)     NOT NULL,
    name   VARCHAR(200)   NOT NULL,
    active NUMBER(1)      DEFAULT 1 NOT NULL,
    CONSTRAINT pk_agency PRIMARY KEY (id),
    CONSTRAINT ux_agency_cif UNIQUE (cif)
);

-- stand-in for AZS_BUSINESS.branch (V002), as the branch sync uses it
CREATE TABLE azs_business.branch (
    branch_code     VARCHAR(10)   NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    city            VARCHAR(100),
    address         VARCHAR(500),
    tel             VARCHAR(50),
    fax             VARCHAR(50),
    zip_code        VARCHAR(20),
    parent_branch   VARCHAR(10)   NOT NULL,
    active          NUMBER(1)     DEFAULT 1 NOT NULL,
    last_synced_at  TIMESTAMP(6)  NOT NULL,
    created_at      TIMESTAMP(6)  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP(6)  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),
    CONSTRAINT pk_branch PRIMARY KEY (branch_code),
    CONSTRAINT fk_branch_parent FOREIGN KEY (parent_branch) REFERENCES azs_business.branch (branch_code)
);

CREATE TABLE azs_auth.jwt_blacklist (
    jti        VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    reason     VARCHAR(50),
    CONSTRAINT pk_jwt_blacklist PRIMARY KEY (jti)
);

-- stand-in for AZS_BUSINESS.request and request_companion (V005): same columns, no CHECK constraints
CREATE TABLE azs_business.request (
    id                        VARBINARY(16)  NOT NULL,
    tracking_code             VARCHAR(20)    NOT NULL,
    request_type              VARCHAR(16)    NOT NULL,
    status                    VARCHAR(32)    NOT NULL,
    parent_request_id         VARBINARY(16),
    agency_id                 VARBINARY(16)  NOT NULL,
    branch_code               VARCHAR(10)    NOT NULL,
    amount                    NUMBER(16)     NOT NULL,
    due_date                  DATE           NOT NULL,
    customer_cif              VARCHAR(20),
    customer_code             VARCHAR(10)    NOT NULL,
    customer_first_name       VARCHAR(100),
    customer_last_name        VARCHAR(100),
    customer_full_name        VARCHAR(200),
    customer_father_name      VARCHAR(100),
    customer_birth_date       DATE,
    customer_mobile           VARCHAR(20),
    customer_ssn              VARCHAR(30),
    customer_cert_serial      VARCHAR(50),
    customer_cert_series      VARCHAR(50),
    customer_personality      VARCHAR(20),
    customer_gender           VARCHAR(20),
    registered_by             VARCHAR(100)   NOT NULL,
    registered_at             TIMESTAMP(6)   NOT NULL,
    version                   NUMBER(10)     DEFAULT 0 NOT NULL,
    created_at                TIMESTAMP(6)   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                TIMESTAMP(6)   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by                VARCHAR(100),
    updated_by                VARCHAR(100),
    CONSTRAINT pk_request PRIMARY KEY (id),
    CONSTRAINT ux_request_tracking_code UNIQUE (tracking_code),
    CONSTRAINT fk_request_agency FOREIGN KEY (agency_id) REFERENCES azs_business.agency (id),
    CONSTRAINT fk_request_branch FOREIGN KEY (branch_code) REFERENCES azs_business.branch (branch_code)
);
CREATE TABLE azs_business.request_companion (
    id               VARBINARY(16)  NOT NULL,
    request_id       VARBINARY(16)  NOT NULL,
    sort_order       NUMBER(2)      NOT NULL,
    full_name        VARCHAR(100)   NOT NULL,
    national_code    VARCHAR(10)    NOT NULL,
    birth_date       DATE           NOT NULL,
    passport_number  VARCHAR(30)    NOT NULL,
    created_at       TIMESTAMP(6)   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_request_companion PRIMARY KEY (id),
    CONSTRAINT fk_companion_request FOREIGN KEY (request_id) REFERENCES azs_business.request (id),
    CONSTRAINT ux_companion_order UNIQUE (request_id, sort_order)
);

-- stand-in for AZS_BUSINESS.tracking_counter (V002)
CREATE TABLE azs_business.tracking_counter (
    branch_code  VARCHAR(10) NOT NULL,
    agency_code  VARCHAR(4)  NOT NULL,
    day          VARCHAR(6)  NOT NULL,
    last_value   NUMBER(4)   DEFAULT 0 NOT NULL,
    CONSTRAINT pk_tracking_counter PRIMARY KEY (branch_code, agency_code, day)
);

-- stand-in for AZS_OUTBOX.outbox_message (V006): same columns, no CHECK constraints
CREATE SCHEMA IF NOT EXISTS azs_outbox;
CREATE TABLE azs_outbox.outbox_message (
    id               VARBINARY(16)  NOT NULL,
    mobile           VARCHAR(11)    NOT NULL,
    template_code    VARCHAR(40)    NOT NULL,
    message_text     VARCHAR(2000)  NOT NULL,
    request_id       VARBINARY(16),
    status           VARCHAR(10)    DEFAULT 'PENDING' NOT NULL,
    attempts         NUMBER(5)      DEFAULT 0 NOT NULL,
    next_attempt_at  TIMESTAMP(6)   NOT NULL,
    sms_id           VARCHAR(100),
    last_error       VARCHAR(500),
    sent_at          TIMESTAMP(6),
    created_at       TIMESTAMP(6)   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP(6)   DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_outbox_message PRIMARY KEY (id)
);
