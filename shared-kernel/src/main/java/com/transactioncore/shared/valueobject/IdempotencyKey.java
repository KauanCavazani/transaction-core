package com.transactioncore.shared.valueobject;

import java.util.UUID;

public record IdempotencyKey(String value) {

    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Idempotency key cannot be null or blank");
        }
    }

    public static IdempotencyKey generate() {
        return new IdempotencyKey(UUID.randomUUID().toString());
    }

    public static IdempotencyKey of(String value) {
        return new IdempotencyKey(value);
    }

    public String redisKey() {
        return "idempotency:" + value;
    }

}
