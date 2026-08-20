CREATE TABLE notification (
    id              UUID         NOT NULL,
    transaction_id  UUID         NOT NULL,
    type            VARCHAR(30)  NOT NULL,
    message         TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_notification PRIMARY KEY (id),

    CONSTRAINT chk_notification_type CHECK (type IN ('TRANSACTION_COMPLETED', 'TRANSACTION_FAILED'))
);

CREATE INDEX idx_notification_transaction_id ON notification (transaction_id);

CREATE TABLE processed_event (
    event_id      UUID         NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_processed_event PRIMARY KEY (event_id)
);