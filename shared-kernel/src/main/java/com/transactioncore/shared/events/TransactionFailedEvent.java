package com.transactioncore.shared.events;

import java.time.Instant;
import java.util.UUID;

public record TransactionFailedEvent(UUID eventId, UUID transactionId, String reasonCode, String reasonMessage, Instant occurredAt) implements DomainEvent {

    public static final String TOPIC = "transactioncore.transactions.failed";

    @Override
    public String topic() {
        return TOPIC;
    }

    public static TransactionFailedEvent create(UUID transactionId, String reasonCode, String reasonMessage) {
        return new TransactionFailedEvent(UUID.randomUUID(), transactionId, reasonCode, reasonMessage, Instant.now());
    }

}
