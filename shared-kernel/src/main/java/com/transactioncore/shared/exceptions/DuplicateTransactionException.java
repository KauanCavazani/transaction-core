package com.transactioncore.shared.exceptions;

import com.transactioncore.shared.valueobject.IdempotencyKey;

public class DuplicateTransactionException extends TransactionCoreException {

    public DuplicateTransactionException(IdempotencyKey idempotencyKey) {
        super("DUPLICATE_TRANSACTION", String.format("Duplicate transaction with idempotency key %s", idempotencyKey.value()));
    }

}
