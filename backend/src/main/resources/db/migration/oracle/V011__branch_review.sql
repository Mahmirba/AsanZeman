-- V011: the branch's work on a request (4.1): the ownership lock that outlives one HTTP call, the rejection the branch
-- chose, and the guarantee number it recorded. Forward-fix only (D20).
ALTER TABLE azs_business.request ADD (
    locked_by           RAW(16),
    locked_by_name      VARCHAR2(100 CHAR),
    locked_at           TIMESTAMP(6),
    last_activity_at    TIMESTAMP(6),
    rejection_reason_id RAW(16),
    rejection_comment   VARCHAR2(200 CHAR),
    guarantee_number    VARCHAR2(30 CHAR),
    issued_at           TIMESTAMP(6)
);
-- a request is locked when its holder is set, and then it also says since when
ALTER TABLE azs_business.request ADD CONSTRAINT ck_request_lock
    CHECK ((locked_by IS NULL AND locked_at IS NULL) OR (locked_by IS NOT NULL AND locked_at IS NOT NULL));
ALTER TABLE azs_business.request ADD CONSTRAINT fk_request_rejection_reason
    FOREIGN KEY (rejection_reason_id) REFERENCES azs_business.rejection_reason (id);
-- one guarantee, one request (NULLs may repeat)
CREATE UNIQUE INDEX azs_business.ux_request_guarantee_number ON azs_business.request (guarantee_number);
-- the idle-lock job looks for reviews nobody touched
CREATE INDEX azs_business.ix_request_review_idle ON azs_business.request (status, last_activity_at);
