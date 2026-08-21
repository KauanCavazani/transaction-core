CREATE TABLE ledger_entry (
    id              UUID            NOT NULL,
    transaction_id  UUID            NOT NULL,
    account_id      UUID            NOT NULL,
    type            VARCHAR(10)     NOT NULL,
    amount          NUMERIC(19, 4)  NOT NULL,
    currency_code   VARCHAR(3)      NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,

    CONSTRAINT pk_ledger_entry PRIMARY KEY (id),

    CONSTRAINT chk_ledger_entry_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_ledger_entry_type CHECK (type IN ('DEBIT', 'CREDIT'))
);

CREATE INDEX idx_ledger_entry_transaction_id ON ledger_entry (transaction_id);
CREATE INDEX idx_ledger_entry_account_id ON ledger_entry (account_id);


CREATE TABLE processed_event (
    event_id       UUID         NOT NULL,
    processed_at   TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_processed_event PRIMARY KEY (event_id)
);


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