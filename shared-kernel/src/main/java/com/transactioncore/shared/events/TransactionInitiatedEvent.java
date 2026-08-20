package com.transactioncore.shared.events;

import com.transactioncore.shared.valueobject.Money;

import java.time.Instant;
import java.util.UUID;

public record TransactionInitiatedEvent(UUID eventId, UUID transactionId, UUID sourceAccountId, UUID destinationAccountId, Money amount, Instant occurredAt) implements DomainEvent {

    @Override
    public String topic() {
        return "transactioncore.transactions.initiated";
    }

    public static TransactionInitiatedEvent create(UUID transactionId, UUID sourceAccountId, UUID destinationAccountId, Money amount) {
        return new TransactionInitiatedEvent(UUID.randomUUID(), transactionId, sourceAccountId, destinationAccountId, amount, Instant.now());
    }

}
