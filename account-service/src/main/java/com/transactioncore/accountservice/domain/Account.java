package com.transactioncore.accountservice.domain;

import com.transactioncore.shared.exceptions.InsufficientFundsException;
import com.transactioncore.shared.valueobject.Money;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "account")
public class Account {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false)
    private BigDecimal balanceAmount;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Account() {}

    public Account(String ownerName, Money initialBalance) {
        this.id = UUID.randomUUID();
        this.ownerName = ownerName;
        this.balanceAmount = initialBalance.amount();
        this.currencyCode = initialBalance.currency().getCurrencyCode();
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Money getBalance() {
        return Money.of(balanceAmount, currencyCode);
    }

    public void debit(Money amount) {
        Money result = getBalance().subtract(amount);

        if (result.isNegative()) {
            throw new InsufficientFundsException(this.id);
        }

        balanceAmount = result.amount();
        updatedAt = Instant.now();
    }

    public void credit(Money amount) {
        Money result = getBalance().add(amount);
        balanceAmount = result.amount();
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public AccountStatus getStatus() {
        return status;
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
