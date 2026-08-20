package com.transactioncore.notificationservice.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {}

    public Notification(UUID transactionId, NotificationType type, String message) {
        this.id = UUID.randomUUID();
        this.transactionId = transactionId;
        this.type = type;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
