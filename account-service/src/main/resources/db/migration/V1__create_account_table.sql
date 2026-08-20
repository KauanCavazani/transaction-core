CREATE TABLE account (
    id              UUID            NOT NULL,
    owner_name      VARCHAR(255)    NOT NULL,
    balance_amount  NUMERIC(19, 4)  NOT NULL,
    currency_code   VARCHAR(3)      NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    version         BIGINT          NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_account PRIMARY KEY (id),

    CONSTRAINT chk_account_balance_non_negative CHECK (balance_amount >= 0),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE INDEX idx_account_owner_name ON account (owner_name);