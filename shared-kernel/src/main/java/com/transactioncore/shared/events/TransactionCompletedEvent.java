package com.transactioncore.shared.events;

import java.time.Instant;
import java.util.UUID;

public record TransactionCompletedEvent(UUID eventId, UUID transactionId, Instant occurredAt) implements DomainEvent {

    public static final String TOPIC = "transactioncore.transactions.completed";

    @Override
    public String topic() {
        return TOPIC;
    }

    public static TransactionCompletedEvent create(UUID transactionId) {
        return new TransactionCompletedEvent(UUID.randomUUID(), transactionId, Instant.now());
    }
}
