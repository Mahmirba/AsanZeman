-- V006: the SMS outbox (Phase 2, 6.4, D40). One recipient per row, written in the same transaction as the status
-- change that causes it. PENDING rows are tried when next_attempt_at is due; SENT rows keep the SMS centre smsId for
-- tracing; DEAD rows (the SMS centre answered 400) are never retried and are kept for ops. Forward-fix only (D20).
CREATE TABLE azs_outbox.outbox_message (
    id               RAW(16)             DEFAULT SYS_GUID() NOT NULL,
    mobile           VARCHAR2(11 CHAR)   NOT NULL,
    template_code    VARCHAR2(40 CHAR)   NOT NULL,
    message_text     VARCHAR2(2000 CHAR) NOT NULL,
    request_id       RAW(16),
    status           VARCHAR2(10 CHAR)   DEFAULT 'PENDING' NOT NULL,
    attempts         NUMBER(5)           DEFAULT 0 NOT NULL,
    next_attempt_at  TIMESTAMP(6)        NOT NULL,
    sms_id           VARCHAR2(100 CHAR),
    last_error       VARCHAR2(500 CHAR),
    sent_at          TIMESTAMP(6),
    created_at       TIMESTAMP(6)        DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at       TIMESTAMP(6)        DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_outbox_message PRIMARY KEY (id),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'SENT', 'DEAD')),
    CONSTRAINT ck_outbox_mobile CHECK (REGEXP_LIKE(mobile, '^09[0-9]{9}$'))
);
CREATE INDEX azs_outbox.ix_outbox_due ON azs_outbox.outbox_message (status, next_attempt_at);
CREATE INDEX azs_outbox.ix_outbox_request ON azs_outbox.outbox_message (request_id);
