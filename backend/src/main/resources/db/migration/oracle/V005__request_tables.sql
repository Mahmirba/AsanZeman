-- V005: requests and their companions (Phase 2, D12, D21, D52).
-- Same conventions as V002: RAW(16) ids with SYS_GUID(), VARCHAR2 in CHAR semantics, nothing partitioned (D43),
-- forward-fix only (D20). The ownership-lock columns (D1) and the review columns arrive with Phase 4, in their own
-- migration. The customer's details are a plain-text snapshot taken at registration (D2, D21, D22).

CREATE TABLE azs_business.request (
    id                        RAW(16)            DEFAULT SYS_GUID() NOT NULL,
    tracking_code             VARCHAR2(20 CHAR)  NOT NULL,
    request_type              VARCHAR2(16 CHAR)  NOT NULL,
    status                    VARCHAR2(32 CHAR)  NOT NULL,
    parent_request_id         RAW(16),
    agency_id                 RAW(16)            NOT NULL,
    branch_code               VARCHAR2(10 CHAR)  NOT NULL,
    amount                    NUMBER(16)         NOT NULL,
    due_date                  DATE               NOT NULL,
    customer_cif              VARCHAR2(20 CHAR),
    customer_code             VARCHAR2(10 CHAR)  NOT NULL,
    customer_first_name       VARCHAR2(100 CHAR),
    customer_last_name        VARCHAR2(100 CHAR),
    customer_full_name        VARCHAR2(200 CHAR),
    customer_father_name      VARCHAR2(100 CHAR),
    customer_birth_date       DATE,
    customer_mobile           VARCHAR2(20 CHAR),
    customer_ssn              VARCHAR2(30 CHAR),
    customer_cert_serial      VARCHAR2(50 CHAR),
    customer_cert_series      VARCHAR2(50 CHAR),
    customer_personality      VARCHAR2(20 CHAR),
    customer_gender           VARCHAR2(20 CHAR),
    registered_by             VARCHAR2(100 CHAR) NOT NULL,
    registered_at             TIMESTAMP(6)       NOT NULL,
    version                   NUMBER(10)         DEFAULT 0 NOT NULL,
    created_at                TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at                TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,
    created_by                VARCHAR2(100 CHAR),
    updated_by                VARCHAR2(100 CHAR),
    CONSTRAINT pk_request PRIMARY KEY (id),
    CONSTRAINT ux_request_tracking_code UNIQUE (tracking_code),
    CONSTRAINT fk_request_parent FOREIGN KEY (parent_request_id) REFERENCES azs_business.request (id),
    CONSTRAINT fk_request_agency FOREIGN KEY (agency_id) REFERENCES azs_business.agency (id),
    CONSTRAINT fk_request_branch FOREIGN KEY (branch_code) REFERENCES azs_business.branch (branch_code),
    CONSTRAINT ck_request_type CHECK (request_type IN ('ISSUANCE', 'REVOCATION')),
    CONSTRAINT ck_request_status CHECK (status IN ('REGISTERED_AT_AGENCY', 'PENDING_BRANCH_REVIEW', 'UNDER_REVIEW',
        'UNDER_ACTION', 'ISSUED', 'REJECTED', 'CANCELLED', 'REVOKED', 'COMPLETED')),
    CONSTRAINT ck_request_amount CHECK (amount > 0),
    CONSTRAINT ck_request_customer_code CHECK (REGEXP_LIKE(customer_code, '^[0-9]{10}$'))
);
-- the agency's list and the duplicate-registration guard (D47)
CREATE INDEX azs_business.ix_request_agency ON azs_business.request (agency_id, customer_code, registered_at);
-- the branch inbox (4.4) and its status filter
CREATE INDEX azs_business.ix_request_branch_status ON azs_business.request (branch_code, status);
-- the customer finds the requests waiting for them by national code
CREATE INDEX azs_business.ix_request_customer ON azs_business.request (customer_code, status);
CREATE INDEX azs_business.ix_request_parent ON azs_business.request (parent_request_id);

CREATE TABLE azs_business.request_companion (
    id               RAW(16)            DEFAULT SYS_GUID() NOT NULL,
    request_id       RAW(16)            NOT NULL,
    sort_order       NUMBER(2)          NOT NULL,
    full_name        VARCHAR2(100 CHAR) NOT NULL,
    national_code    VARCHAR2(10 CHAR)  NOT NULL,
    birth_date       DATE               NOT NULL,
    passport_number  VARCHAR2(30 CHAR)  NOT NULL,
    created_at       TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_request_companion PRIMARY KEY (id),
    CONSTRAINT fk_companion_request FOREIGN KEY (request_id) REFERENCES azs_business.request (id),
    CONSTRAINT ux_companion_order UNIQUE (request_id, sort_order),
    CONSTRAINT ck_companion_order CHECK (sort_order BETWEEN 1 AND 5),
    CONSTRAINT ck_companion_code CHECK (REGEXP_LIKE(national_code, '^[0-9]{10}$'))
);
