package com.transactioncore.shared.exceptions;

import java.util.UUID;

public class InsufficientFundsException extends TransactionCoreException {

    public InsufficientFundsException (UUID accountId) {
        super("INSUFFICIENT_FUNDS", String.format("Insufficient funds in account %s", accountId));
    }

}
