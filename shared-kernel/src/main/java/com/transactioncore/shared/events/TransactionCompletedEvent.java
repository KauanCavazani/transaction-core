package com.transactioncore.shared.events;

import java.time.Instant;
import java.util.UUID;

public record TransactionCompletedEvent(UUID eventId, UUID transactionId, Instant occurredAt) implements DomainEvent {

    @Override
    public String topic() {
        return "transactioncore.transactions.completed";
    }

    public static TransactionCompletedEvent create(UUID transactionId) {
        return new TransactionCompletedEvent(UUID.randomUUID(), transactionId, Instant.now());
    }
}
