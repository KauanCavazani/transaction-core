package com.transactioncore.shared.exceptions;

import java.util.UUID;

public class AccountNotOperationalException extends TransactionCoreException {

    public AccountNotOperationalException(UUID accountId) {
        super("ACCOUNT_NOT_OPERATIONAL", String.format("Account %s is not operational", accountId));
    }

}