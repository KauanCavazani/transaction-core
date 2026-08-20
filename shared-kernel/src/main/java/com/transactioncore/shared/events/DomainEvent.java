package com.transactioncore.shared.events;

import java.time.Instant;
import java.util.UUID;

public sealed interface DomainEvent permits TransactionInitiatedEvent, TransactionCompletedEvent, TransactionFailedEvent {
    UUID eventId();
    UUID transactionId();
    Instant occurredAt();
    String topic();
}
