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

CREATE TABLE azs_auth.jwt_blacklist (
    jti        VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    reason     VARCHAR(50),
    CONSTRAINT pk_jwt_blacklist PRIMARY KEY (jti)
);
