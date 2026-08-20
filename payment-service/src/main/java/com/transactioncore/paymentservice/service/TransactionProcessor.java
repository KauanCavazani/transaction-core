package com.transactioncore.paymentservice.service;

import com.transactioncore.paymentservice.client.AccountClient;
import com.transactioncore.paymentservice.client.dto.AccountAvailability;
import com.transactioncore.paymentservice.domain.Transaction;
import com.transactioncore.paymentservice.messaging.OutboxEventPublisher;
import com.transactioncore.paymentservice.repository.TransactionRepository;
import com.transactioncore.shared.events.TransactionInitiatedEvent;
import com.transactioncore.shared.exceptions.AccountNotFoundException;
import com.transactioncore.shared.exceptions.AccountNotOperationalException;
import com.transactioncore.shared.exceptions.DuplicateTransactionException;
import com.transactioncore.shared.valueobject.IdempotencyKey;
import com.transactioncore.shared.valueobject.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionProcessor {

    private final TransactionRepository repository;
    private final AccountClient accountClient;
    private final OutboxEventPublisher outboxEventPublisher;

    public TransactionProcessor(TransactionRepository repository, AccountClient accountClient, OutboxEventPublisher outboxEventPublisher) {
        this.repository = repository;
        this.accountClient = accountClient;
        this.outboxEventPublisher = outboxEventPublisher;
    }

    @Transactional
    public Transaction processTransfer(IdempotencyKey idempotencyKey, UUID sourceAccountId, UUID destinationAccountId, Money amount) {
        Optional<Transaction> existingTransaction = repository.findByIdempotencyKey(idempotencyKey.value());
        if (existingTransaction.isPresent()) {
            Transaction transaction = existingTransaction.get();
            if (transaction.matches(sourceAccountId, destinationAccountId, amount)) {
                return transaction;
            }
            throw new DuplicateTransactionException(idempotencyKey);
        }

        validateAccountIsOperational(sourceAccountId);
        validateAccountIsOperational(destinationAccountId);

        Transaction newTransaction = new Transaction(idempotencyKey.value(), sourceAccountId, destinationAccountId, amount);
        newTransaction = repository.save(newTransaction);

        TransactionInitiatedEvent event = TransactionInitiatedEvent.create(newTransaction.getId(), sourceAccountId, destinationAccountId, amount);
        outboxEventPublisher.publish(event);

        return newTransaction;
    }

    private void validateAccountIsOperational(UUID accountId) {
        AccountAvailability availability = accountClient.checkAvailability(accountId.toString());
        switch (availability) {
            case NOT_FOUND -> throw new AccountNotFoundException(accountId);
            case INACTIVE -> throw new AccountNotOperationalException(accountId);
            case ACTIVE -> { /* Account is operational, do nothing */ }
        }
    }

}
