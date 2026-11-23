-- V010: the collateral a customer chose for a request (4.3): one row per deposit allocation. Balances are never
-- stored (D16): only what was taken from or blocked in each deposit, and what that is worth to the bank.
-- Forward-fix only (D20).
CREATE TABLE azs_business.collateral (
    id             RAW(16)            DEFAULT SYS_GUID() NOT NULL,
    request_id     RAW(16)            NOT NULL,
    deposit_number VARCHAR2(30 CHAR)  NOT NULL,
    kind           VARCHAR2(10 CHAR)  NOT NULL,
    price_class    VARCHAR2(10 CHAR)  NOT NULL,
    share_percent  NUMBER(3)          NOT NULL,
    factor         NUMBER(5, 2)       NOT NULL,
    allocated      NUMBER(19)         NOT NULL,
    collateral     NUMBER(19)         NOT NULL,
    created_at     TIMESTAMP(6)       DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_collateral PRIMARY KEY (id),
    CONSTRAINT fk_collateral_request FOREIGN KEY (request_id) REFERENCES azs_business.request (id),
    CONSTRAINT ck_collateral_kind CHECK (kind IN ('CASH', 'PLEDGE')),
    CONSTRAINT ck_collateral_price_class CHECK (price_class IN ('CHEAP', 'EXPENSIVE')),
    CONSTRAINT ck_collateral_amounts CHECK (allocated > 0 AND collateral >= allocated)
);
CREATE INDEX azs_business.ix_collateral_request ON azs_business.collateral (request_id);
