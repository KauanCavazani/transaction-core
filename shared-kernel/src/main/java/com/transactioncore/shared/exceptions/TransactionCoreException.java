package com.transactioncore.shared.exceptions;

public abstract class TransactionCoreException extends RuntimeException {

    private final String errorCode;

    protected TransactionCoreException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected TransactionCoreException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

}
