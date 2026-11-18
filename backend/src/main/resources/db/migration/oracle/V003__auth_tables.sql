-- V003: bank users, refresh tokens and the revoked access-token list (Phase 1, 6.2, D5, D7, D33).
-- Same conventions as V002: RAW(16) ids with SYS_GUID(), VARCHAR2 in CHAR semantics, nothing partitioned (D43),
-- forward-fix only (D20).

-------------------------------------------------------------------------------
-- AZS_AUTH.app_user - bank users, filled from personnelInfo at every login (D33)
-------------------------------------------------------------------------------
CREATE TABLE azs_auth.app_user (
    id                 RAW(16)            DEFAULT SYS_GUID() NOT NULL,
    account_name       VARCHAR2(100 CHAR) NOT NULL,
    prsn_code          VARCHAR2(50 CHAR),
    personel_user_name VARCHAR2(100 CHAR),
    branch_code        VARCHAR2(10 CHAR)  NOT NULL,
    branch_name        VARCHAR2(200 CHAR),
    job_position       VARCHAR2(200 CHAR),
    job                VARCHAR2(200 CHAR),
    role               VARCHAR2(16 CHAR)  NOT NULL,
    last_login_at      TIMESTAMP(6)       NOT NULL,
    created_at         TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at         TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,
    created_by         VARCHAR2(100 CHAR),
    updated_by         VARCHAR2(100 CHAR),
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT ux_app_user_account_name UNIQUE (account_name),
    CONSTRAINT ck_app_user_role CHECK (role IN ('BRANCH', 'ADMIN'))
);

-------------------------------------------------------------------------------
-- AZS_AUTH.refresh_tokens - only a SHA-256 hash of each token is stored; one family per login (6, D7)
-- subject_id is the bank user's or the agency's id; role and branch_code are the session's snapshot, so the
-- branch stays fixed for the whole session (D33). expires_at is the end of the session and does not move on rotation.
-------------------------------------------------------------------------------
CREATE TABLE azs_auth.refresh_tokens (
    id           RAW(16)            DEFAULT SYS_GUID() NOT NULL,
    family_id    RAW(16)            NOT NULL,
    subject_id   RAW(16)            NOT NULL,
    subject_name VARCHAR2(100 CHAR) NOT NULL,
    role         VARCHAR2(16 CHAR)  NOT NULL,
    branch_code  VARCHAR2(10 CHAR),
    token_hash   VARCHAR2(64 CHAR)  NOT NULL,
    expires_at   TIMESTAMP(6)       NOT NULL,
    used_at      TIMESTAMP(6),
    revoked_at   TIMESTAMP(6),
    created_at   TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT ux_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX azs_auth.ix_refresh_tokens_family ON azs_auth.refresh_tokens (family_id);
CREATE INDEX azs_auth.ix_refresh_tokens_expires ON azs_auth.refresh_tokens (expires_at);

-------------------------------------------------------------------------------
-- AZS_AUTH.jwt_blacklist - access tokens revoked before they expire (logout); rows are only needed until expires_at
-------------------------------------------------------------------------------
CREATE TABLE azs_auth.jwt_blacklist (
    jti        VARCHAR2(64 CHAR) NOT NULL,
    expires_at TIMESTAMP(6)      NOT NULL,
    revoked_at TIMESTAMP(6)      DEFAULT SYSTIMESTAMP NOT NULL,
    reason     VARCHAR2(50 CHAR),
    CONSTRAINT pk_jwt_blacklist PRIMARY KEY (jti)
);
CREATE INDEX azs_auth.ix_jwt_blacklist_expires ON azs_auth.jwt_blacklist (expires_at);
