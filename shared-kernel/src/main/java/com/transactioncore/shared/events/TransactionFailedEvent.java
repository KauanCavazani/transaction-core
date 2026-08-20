package com.transactioncore.shared.events;

import java.time.Instant;
import java.util.UUID;

public record TransactionFailedEvent(UUID eventId, UUID transactionId, String reasonCode, String reasonMessage, Instant occurredAt) implements DomainEvent {

    @Override
    public String topic() {
        return "transactioncore.transactions.failed";
    }

    public static TransactionFailedEvent create(UUID transactionId, String reasonCode, String reasonMessage) {
        return new TransactionFailedEvent(UUID.randomUUID(), transactionId, reasonCode, reasonMessage, Instant.now());
    }

}
