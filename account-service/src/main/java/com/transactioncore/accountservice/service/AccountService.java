package com.transactioncore.accountservice.service;

import com.transactioncore.accountservice.domain.Account;
import com.transactioncore.accountservice.repository.AccountRepository;
import com.transactioncore.shared.exceptions.AccountNotFoundException;
import com.transactioncore.shared.valueobject.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
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

    @Transactional
    public void debit(UUID accountId, Money amount) {
        Account account = findById(accountId);
        account.debit(amount);
        repository.save(account);
    }

    @Transactional
    public void credit(UUID accountId, Money amount) {
        Account account = findById(accountId);
        account.credit(amount);
        repository.save(account);
    }

}
