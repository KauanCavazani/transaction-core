package com.transactioncore.ledgerservice.domain;

import com.transactioncore.shared.valueobject.Money;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID transactionId;

    @Column(nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    private LedgerEntryType type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {}

    public LedgerEntry(UUID transactionId, UUID accountId, LedgerEntryType type, Money amount) {
        this.id = UUID.randomUUID();
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount.amount();
        this.currencyCode = amount.currency().getCurrencyCode();
        this.createdAt = Instant.now();
    }

    public Money getAmount() {
        return Money.of(amount, currencyCode);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public LedgerEntryType getType() {
        return type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
