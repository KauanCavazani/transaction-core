package com.transactioncore.shared.events;

import com.transactioncore.shared.valueobject.Money;

import java.time.Instant;
import java.util.UUID;

public record TransactionInitiatedEvent(UUID eventId, UUID transactionId, UUID sourceAccountId, UUID destinationAccountId, Money amount, Instant occurredAt) implements DomainEvent {

    public static final String TOPIC = "transactioncore.transactions.initiated";

    @Override
    public String topic() {
        return TOPIC;
    }

    public static TransactionInitiatedEvent create(UUID transactionId, UUID sourceAccountId, UUID destinationAccountId, Money amount) {
        return new TransactionInitiatedEvent(UUID.randomUUID(), transactionId, sourceAccountId, destinationAccountId, amount, Instant.now());
    }

}
