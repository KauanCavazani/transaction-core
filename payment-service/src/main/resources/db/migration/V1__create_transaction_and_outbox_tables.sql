CREATE TABLE transaction (
    id                      UUID            NOT NULL,
    idempotency_key         VARCHAR(255)    NOT NULL,
    source_account_id       UUID            NOT NULL,
    destination_account_id  UUID            NOT NULL,
    amount                  NUMERIC(19, 4)  NOT NULL,
    currency_code           VARCHAR(3)      NOT NULL,
    status                  VARCHAR(20)     NOT NULL,
    failure_reason_code     VARCHAR(50),
    version                 BIGINT          NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL,
    updated_at              TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_transaction PRIMARY KEY (id),

    CONSTRAINT uq_transaction_idempotency_key UNIQUE (idempotency_key),

    CONSTRAINT chk_transaction_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transaction_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),

    CONSTRAINT chk_transaction_different_accounts CHECK (source_account_id <> destination_account_id)
);

CREATE INDEX idx_transaction_source_account ON transaction (source_account_id);
CREATE INDEX idx_transaction_destination_account ON transaction (destination_account_id);

CREATE TABLE outbox_event (
    id            UUID         NOT NULL,
    aggregate_id  UUID         NOT NULL,
    event_type    VARCHAR(255) NOT NULL,
    topic         VARCHAR(255) NOT NULL,
    payload       TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_outbox_event PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_event_created_at ON outbox_event (created_at);