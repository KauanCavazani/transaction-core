package com.transactioncore.shared.exceptions;

import com.transactioncore.shared.valueobject.IdempotencyKey;

public class TransactionProcessingException extends TransactionCoreException {

    public TransactionProcessingException(IdempotencyKey idempotencyKey) {
        super("TRANSACTION_PROCESSING", "Transaction with idempotency key " + idempotencyKey.value() + " is currently being processed, try again shortly");
    }

}
