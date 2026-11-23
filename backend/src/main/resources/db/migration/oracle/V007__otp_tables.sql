-- V007: customer login by SMS code (6.3, D34). The Notifier makes and checks the 4-digit code, so none is stored here:
-- only the id it gave us (otp_id), when it was sent, and whether it was used. Forward-fix only (D20).
CREATE TABLE azs_auth.otp_challenge (
    id            RAW(16)            DEFAULT SYS_GUID() NOT NULL,
    national_code VARCHAR2(10 CHAR)  NOT NULL,
    otp_id        VARCHAR2(100 CHAR) NOT NULL,
    family_id     RAW(16)            NOT NULL,
    sent_at       TIMESTAMP(6)       NOT NULL,
    status        VARCHAR2(10 CHAR)  DEFAULT 'SENT' NOT NULL,
    CONSTRAINT pk_otp_challenge PRIMARY KEY (id),
    CONSTRAINT ck_otp_challenge_status CHECK (status IN ('SENT', 'VERIFIED'))
);

-- Wrong codes are counted per customer, not per code, so asking for a new code does not reset the count (D34).
CREATE TABLE azs_auth.otp_lockout (
    national_code  VARCHAR2(10 CHAR) NOT NULL,
    wrong_attempts NUMBER(3)         DEFAULT 0 NOT NULL,
    locked_until   TIMESTAMP(6),
    CONSTRAINT pk_otp_lockout PRIMARY KEY (national_code)
);
