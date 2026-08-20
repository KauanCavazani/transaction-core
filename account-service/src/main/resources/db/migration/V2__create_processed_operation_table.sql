CREATE TABLE processed_operation (
    operation_id  UUID         NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_processed_operation PRIMARY KEY (operation_id)
);

CREATE INDEX idx_processed_operation_processed_at ON processed_operation (processed_at);