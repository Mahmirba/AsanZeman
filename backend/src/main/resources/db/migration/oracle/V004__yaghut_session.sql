-- V004: the agency's and customer's own Yaghut session, kept server-side next to their Asan Zeman session
-- (5.4, D7, D35). One row per login, keyed by the refresh-token family. The cookie never reaches a panel.
-- Rows are only needed until expires_at; the scheduler purges them (Phase 8). Forward-fix only (D20).
CREATE TABLE azs_auth.yaghut_session (
    family_id   RAW(16)            NOT NULL,
    jsession_id VARCHAR2(200 CHAR) NOT NULL,
    expires_at  TIMESTAMP(6),
    created_at  TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_yaghut_session PRIMARY KEY (family_id)
);
CREATE INDEX azs_auth.ix_yaghut_session_expires ON azs_auth.yaghut_session (expires_at);
