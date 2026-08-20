package com.transactioncore.shared.exceptions;

import java.util.UUID;

public class AccountNotFoundException extends TransactionCoreException {

    public AccountNotFoundException(UUID accountId) {
        super("ACCOUNT_NOT_FOUND", String.format("Account with ID %s not found", accountId));
    }

}
