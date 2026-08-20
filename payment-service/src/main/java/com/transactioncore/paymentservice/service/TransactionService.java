package com.transactioncore.paymentservice.service;

import com.transactioncore.paymentservice.cache.IdempotencyCache;
import com.transactioncore.paymentservice.domain.Transaction;
import com.transactioncore.paymentservice.repository.TransactionRepository;
import com.transactioncore.shared.exceptions.TransactionProcessingException;
import com.transactioncore.shared.valueobject.IdempotencyKey;
import com.transactioncore.shared.valueobject.Money;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository repository;
    private final TransactionProcessor transactionProcessor;
    private final IdempotencyCache idempotencyCache;

    public TransactionService(TransactionRepository repository, TransactionProcessor transactionProcessor, IdempotencyCache idempotencyCache) {
        this.repository = repository;
        this.transactionProcessor = transactionProcessor;
        this.idempotencyCache = idempotencyCache;
    }

    public Transaction transfer(IdempotencyKey idempotencyKey, UUID sourceAccountId, UUID destinationAccountId, Money amount) {
        Optional<Transaction> cached = tryGetFromCache(idempotencyKey.value());
        if (cached.isPresent()) {
            return cached.get();
        }

        boolean lockAcquired = idempotencyCache.tryLock(idempotencyKey.value());
        if (!lockAcquired) {
            throw new TransactionProcessingException(idempotencyKey);
        }

        try {
            Transaction transaction = transactionProcessor.processTransfer(idempotencyKey, sourceAccountId, destinationAccountId, amount);
            idempotencyCache.store(idempotencyKey.value(), transaction.getId());
            return transaction;
        } finally {
            idempotencyCache.releaseLock(idempotencyKey.value());
        }
    }

    private Optional<Transaction> tryGetFromCache(String idempotencyKey) {
        Optional<UUID> cachedTransactionId = idempotencyCache.findTransactionId(idempotencyKey);
        return cachedTransactionId.flatMap(repository::findById);
    }

}
