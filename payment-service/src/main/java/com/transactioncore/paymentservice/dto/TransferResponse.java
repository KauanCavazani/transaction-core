package com.transactioncore.paymentservice.dto;

import com.transactioncore.paymentservice.domain.Transaction;
import com.transactioncore.paymentservice.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static TransferResponse from(Transaction transaction) {
        return new TransferResponse(
                transaction.getId(),
                transaction.getSourceAccountId(),
                transaction.getDestinationAccountId(),
                transaction.getAmount().amount(),
                transaction.getAmount().currency().getCurrencyCode(),
                transaction.getStatus(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }

}
