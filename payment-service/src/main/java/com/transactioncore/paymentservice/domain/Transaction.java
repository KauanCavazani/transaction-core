package com.transactioncore.paymentservice.domain;

import com.transactioncore.shared.valueobject.IdempotencyKey;
import com.transactioncore.shared.valueobject.Money;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transaction")
public class Transaction {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private UUID sourceAccountId;

    @Column(nullable = false)
    private UUID destinationAccountId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Column(nullable = true)
    private String failureReasonCode;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Transaction() {}

    public Transaction(String idempotencyKey, UUID sourceAccountId, UUID destinationAccountId, Money amount) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount.amount();
        this.currencyCode = amount.currency().getCurrencyCode();
        this.status = TransactionStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Money getAmount() {
        return Money.of(amount, currencyCode);
    }

    public void markAsCompleted() {
        this.status = TransactionStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void markAsFailed(String reasonCode) {
        this.status = TransactionStatus.FAILED;
        this.failureReasonCode = reasonCode;
        this.updatedAt = Instant.now();
    }

    public boolean matches(UUID sourceAccountId, UUID destinationAccountId, Money amount) {
        return this.sourceAccountId.equals(sourceAccountId) &&
               this.destinationAccountId.equals(destinationAccountId) &&
               this.amount.compareTo(amount.amount()) == 0 &&
               this.currencyCode.equals(amount.currency().getCurrencyCode());
    }

    public UUID getId() {
        return id;
    }

    public IdempotencyKey getIdempotencyKey() {
        return IdempotencyKey.of(idempotencyKey);
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getFailureReasonCode() {
        return failureReasonCode;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
