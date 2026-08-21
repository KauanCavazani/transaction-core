package com.transactioncore.accountservice.service;

import com.transactioncore.accountservice.domain.Account;
import com.transactioncore.accountservice.domain.ProcessedOperation;
import com.transactioncore.accountservice.repository.AccountRepository;
import com.transactioncore.accountservice.repository.ProcessedOperationRepository;
import com.transactioncore.shared.exceptions.AccountNotFoundException;
import com.transactioncore.shared.valueobject.Money;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository repository;
    private final ProcessedOperationRepository processedOperationRepository;

    public AccountService(AccountRepository repository, ProcessedOperationRepository processedOperationRepository) {
        this.repository = repository;
        this.processedOperationRepository = processedOperationRepository;
    }

    @Transactional
    public Account create(String ownerName, Money initialBalance) {
        Account newAccount = new Account(ownerName, initialBalance);
        return repository.save(newAccount);
    }

    public Account findById(UUID accountId) {
        return repository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    @Retryable(
            includes = ObjectOptimisticLockingFailureException.class,
            maxRetries = 2,
            delay = 100
    )
    @Transactional
    public void debit(UUID accountId, UUID operationId, Money amount) {
        if (isOperationProcessed(operationId)) return;
        Account account = findById(accountId);
        account.debit(amount);
        repository.save(account);
        processedOperationRepository.save(new ProcessedOperation(operationId));
    }

    @Retryable(
            includes = ObjectOptimisticLockingFailureException.class,
            maxRetries = 2,
            delay = 100
    )
    @Transactional
    public void credit(UUID accountId, UUID operationId, Money amount) {
        if (isOperationProcessed(operationId)) return;
        Account account = findById(accountId);
        account.credit(amount);
        repository.save(account);
        processedOperationRepository.save(new ProcessedOperation(operationId));
    }

    private boolean isOperationProcessed(UUID operationId) {
        return processedOperationRepository.existsById(operationId);
    }

}
