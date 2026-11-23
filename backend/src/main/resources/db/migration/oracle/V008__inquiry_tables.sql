-- V008: the inquiry runs and their results (5.1). Forward-fix only (D20).
-- active_request_id equals request_id while a run is RUNNING and is NULL otherwise, so the unique key allows only
-- one running run per request: a double click can never start two (Oracle lets a unique key repeat NULLs).
CREATE TABLE azs_inquiry.inquiry_run (
    id                RAW(16)           NOT NULL,
    request_id        RAW(16)           NOT NULL,
    status            VARCHAR2(10 CHAR) NOT NULL,
    decision          VARCHAR2(10 CHAR),
    active_request_id RAW(16),
    started_at        TIMESTAMP(6)      NOT NULL,
    finished_at       TIMESTAMP(6),
    CONSTRAINT pk_inquiry_run PRIMARY KEY (id),
    CONSTRAINT ux_inquiry_run_active UNIQUE (active_request_id),
    CONSTRAINT ck_inquiry_run_status CHECK (status IN ('RUNNING', 'FINISHED', 'STALE')),
    CONSTRAINT ck_inquiry_run_decision CHECK (decision IN ('REJECT', 'BLOCKED', 'PROCEED'))
);
CREATE INDEX azs_inquiry.ix_inquiry_run_request ON azs_inquiry.inquiry_run (request_id, started_at);

-- One row per inquiry per run. gender and birth_date are only set for Sabte Ahval, the source of both (D26).
CREATE TABLE azs_inquiry.inquiry_result (
    run_id       RAW(16)            NOT NULL,
    inquiry_type VARCHAR2(20 CHAR)  NOT NULL,
    result       VARCHAR2(10 CHAR)  NOT NULL,
    failure_kind VARCHAR2(15 CHAR),
    message_key  VARCHAR2(100 CHAR),
    gender       VARCHAR2(10 CHAR),
    birth_date   DATE,
    finished_at  TIMESTAMP(6)       NOT NULL,
    CONSTRAINT pk_inquiry_result PRIMARY KEY (run_id, inquiry_type),
    CONSTRAINT fk_inquiry_result_run FOREIGN KEY (run_id) REFERENCES azs_inquiry.inquiry_run (id),
    CONSTRAINT ck_inquiry_result_result CHECK (result IN ('PASSED', 'NEGATIVE', 'FAILED', 'SKIPPED'))
);
