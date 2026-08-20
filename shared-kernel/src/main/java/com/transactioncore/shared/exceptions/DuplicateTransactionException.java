package com.transactioncore.shared.exceptions;

import com.transactioncore.shared.valueobject.IdempotencyKey;

import java.util.UUID;

public class DuplicateTransactionException extends TransactionCoreException {

    public DuplicateTransactionException(IdempotencyKey idempotencyKey) {
        super("DUPLICATE_TRANSACTION", String.format("Duplicate transaction with idempotency key %s", idempotencyKey.value()));
    }

}
