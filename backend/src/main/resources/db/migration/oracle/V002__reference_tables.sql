-- V002: reference tables that exist before any request is registered (Phase 0).
-- Conventions: RAW(16) ids with SYS_GUID() (5-1), audit columns on every table, VARCHAR2 in CHAR
-- semantics so Persian text fits. Nothing is partitioned (D43).

-------------------------------------------------------------------------------
-- AZS_BUSINESS.branch - synced nightly from Yaghut (D32); parent_branch set only by db/dba (4-3-5)
-------------------------------------------------------------------------------
CREATE TABLE azs_business.branch (
    branch_code     VARCHAR2(10 CHAR)  NOT NULL,
    name            VARCHAR2(200 CHAR) NOT NULL,
    city            VARCHAR2(100 CHAR),
    address         VARCHAR2(500 CHAR),
    tel             VARCHAR2(50 CHAR),
    fax             VARCHAR2(50 CHAR),
    zip_code        VARCHAR2(20 CHAR),
    parent_branch   VARCHAR2(10 CHAR)  NOT NULL,
    active          NUMBER(1)          DEFAULT 1 NOT NULL,
    last_synced_at  TIMESTAMP(6)       NOT NULL,
    created_at      TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,
    created_by      VARCHAR2(100 CHAR),
    updated_by      VARCHAR2(100 CHAR),
    CONSTRAINT pk_branch PRIMARY KEY (branch_code),
    CONSTRAINT fk_branch_parent FOREIGN KEY (parent_branch) REFERENCES azs_business.branch (branch_code),
    CONSTRAINT ck_branch_active CHECK (active IN (0, 1))
);
CREATE INDEX azs_business.ix_branch_parent ON azs_business.branch (parent_branch);

-------------------------------------------------------------------------------
-- AZS_BUSINESS.agency - travel agencies allowed to register requests; cif = login identity (D35)
-------------------------------------------------------------------------------
CREATE TABLE azs_business.agency (
    id          RAW(16)            DEFAULT SYS_GUID() NOT NULL,
    cif         VARCHAR2(20 CHAR)  NOT NULL,
    code        VARCHAR2(4 CHAR)   NOT NULL,
    name        VARCHAR2(200 CHAR) NOT NULL,
    active      NUMBER(1)          DEFAULT 1 NOT NULL,
    version     NUMBER(10)         DEFAULT 0 NOT NULL,
    created_at  TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,
    created_by  VARCHAR2(100 CHAR),
    updated_by  VARCHAR2(100 CHAR),
    CONSTRAINT pk_agency PRIMARY KEY (id),
    CONSTRAINT ux_agency_cif UNIQUE (cif),
    CONSTRAINT ux_agency_code UNIQUE (code),
    CONSTRAINT ck_agency_active CHECK (active IN (0, 1)),
    CONSTRAINT ck_agency_code_digits CHECK (REGEXP_LIKE(code, '^[0-9]{4}$'))
);

-------------------------------------------------------------------------------
-- AZS_BUSINESS.rejection_reason - admin-managed; kind separates issuance from revocation reasons
-------------------------------------------------------------------------------
CREATE TABLE azs_business.rejection_reason (
    id          RAW(16)            DEFAULT SYS_GUID() NOT NULL,
    kind        VARCHAR2(16 CHAR)  NOT NULL,
    text        VARCHAR2(500 CHAR) NOT NULL,
    active      NUMBER(1)          DEFAULT 1 NOT NULL,
    sort_order  NUMBER(5)          DEFAULT 0 NOT NULL,
    created_at  TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,
    created_by  VARCHAR2(100 CHAR),
    updated_by  VARCHAR2(100 CHAR),
    CONSTRAINT pk_rejection_reason PRIMARY KEY (id),
    CONSTRAINT ck_rejection_reason_kind CHECK (kind IN ('ISSUANCE', 'REVOCATION')),
    CONSTRAINT ck_rejection_reason_active CHECK (active IN (0, 1))
);

-------------------------------------------------------------------------------
-- AZS_BUSINESS.scheduler_lock - ShedLock table for leader election (3-3-7)
-------------------------------------------------------------------------------
CREATE TABLE azs_business.scheduler_lock (
    name        VARCHAR2(64 CHAR)  NOT NULL,
    lock_until  TIMESTAMP(3)       NOT NULL,
    locked_at   TIMESTAMP(3)       NOT NULL,
    locked_by   VARCHAR2(255 CHAR) NOT NULL,
    CONSTRAINT pk_scheduler_lock PRIMARY KEY (name)
);

-------------------------------------------------------------------------------
-- AZS_BUSINESS.tracking_counter - per branch + agency + Jalali day (D12)
-------------------------------------------------------------------------------
CREATE TABLE azs_business.tracking_counter (
    branch_code  VARCHAR2(10 CHAR) NOT NULL,
    agency_code  VARCHAR2(4 CHAR)  NOT NULL,
    day          VARCHAR2(6 CHAR)  NOT NULL,
    last_value   NUMBER(4)         DEFAULT 0 NOT NULL,
    CONSTRAINT pk_tracking_counter PRIMARY KEY (branch_code, agency_code, day),
    CONSTRAINT ck_tracking_counter_range CHECK (last_value BETWEEN 0 AND 9999)
);

-------------------------------------------------------------------------------
-- AZS_AUDIT.audit_log - append only (6-5); the app user gets INSERT only (see db/dba)
-------------------------------------------------------------------------------
CREATE TABLE azs_audit.audit_log (
    id           RAW(16)            DEFAULT SYS_GUID() NOT NULL,
    user_id      RAW(16),
    user_name    VARCHAR2(100 CHAR),
    user_role    VARCHAR2(16 CHAR),
    entity_type  VARCHAR2(64 CHAR)  NOT NULL,
    entity_id    RAW(16),
    action       VARCHAR2(32 CHAR)  NOT NULL,
    old_value    CLOB,
    new_value    CLOB,
    reason       VARCHAR2(500 CHAR),
    ip_address   VARCHAR2(45 CHAR),
    user_agent   VARCHAR2(500 CHAR),
    correlation_id VARCHAR2(64 CHAR),
    created_at   TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);
CREATE INDEX azs_audit.ix_audit_entity ON azs_audit.audit_log (entity_type, entity_id, created_at);
CREATE INDEX azs_audit.ix_audit_user ON azs_audit.audit_log (user_id, created_at);
