package com.transactioncore.accountservice.dto;

import com.transactioncore.accountservice.domain.Account;
import com.transactioncore.accountservice.domain.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String ownerName,
        BigDecimal balanceAmount,
        String currency,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwnerName(),
                account.getBalance().amount(),
                account.getBalance().currency().getCurrencyCode(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

}
