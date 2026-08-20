package com.transactioncore.accountservice.service;

import com.transactioncore.accountservice.domain.Account;
import com.transactioncore.accountservice.repository.AccountRepository;
import com.transactioncore.shared.exceptions.AccountNotFoundException;
import com.transactioncore.shared.exceptions.InsufficientFundsException;
import com.transactioncore.shared.valueobject.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository repository;

    private AccountService accountService;

    private final UUID accountId = UUID.randomUUID();

    private AccountService newService() {
        return new AccountService(repository);
    }

    @Test
    @DisplayName("create should save the account and return what the repository returns")
    void createShouldSaveTheAccountAndReturnWhatTheRepositoryReturns() {
        accountService = newService();
        Money initialBalance = Money.brl(new BigDecimal("500.00"));
        Account savedAccount = new Account("Kauan Brianez", initialBalance);

        when(repository.save(any(Account.class))).thenReturn(savedAccount);

        Account result = accountService.create("Kauan Brianez", initialBalance);

        assertThat(result).isEqualTo(savedAccount);
        verify(repository, times(1)).save(any(Account.class));
    }

    @Test
    @DisplayName("create should pass an account with the requested owner name and balance to save")
    void createShouldPassAnAccountWithTheRequestedOwnerNameAndBalanceToSave() {
        accountService = newService();
        Money initialBalance = Money.brl(new BigDecimal("500.00"));
        when(repository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.create("Kauan Brianez", initialBalance);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(repository).save(captor.capture());
        Account captured = captor.getValue();

        assertThat(captured.getOwnerName()).isEqualTo("Kauan Brianez");
        assertThat(captured.getBalance()).isEqualTo(initialBalance);
    }

    @Test
    @DisplayName("findById should return the account when it exists")
    void findByIdShouldReturnTheAccountWhenItExists() {
        accountService = newService();
        Account existingAccount = new Account("Kauan Brianez", Money.brl(new BigDecimal("100.00")));
        when(repository.findById(accountId)).thenReturn(Optional.of(existingAccount));

        Account result = accountService.findById(accountId);

        assertThat(result).isEqualTo(existingAccount);
    }

    @Test
    @DisplayName("findById should throw AccountNotFoundException when the account does not exist")
    void findByIdShouldThrowAccountNotFoundExceptionWhenTheAccountDoesNotExist() {
        accountService = newService();
        when(repository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.findById(accountId))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("debit should reduce the balance and save the account")
    void debitShouldReduceTheBalanceAndSaveTheAccount() {
        accountService = newService();
        Account existingAccount = new Account("Kauan Brianez", Money.brl(new BigDecimal("100.00")));
        when(repository.findById(accountId)).thenReturn(Optional.of(existingAccount));
        when(repository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.debit(accountId, Money.brl(new BigDecimal("30.00")));

        assertThat(existingAccount.getBalance()).isEqualTo(Money.brl(new BigDecimal("70.00")));
        verify(repository, times(1)).save(existingAccount);
    }

    @Test
    @DisplayName("debit should propagate InsufficientFundsException and not save when balance is too low")
    void debitShouldPropagateInsufficientFundsExceptionAndNotSaveWhenBalanceIsTooLow() {
        accountService = newService();
        Account existingAccount = new Account("Kauan Brianez", Money.brl(new BigDecimal("10.00")));
        when(repository.findById(accountId)).thenReturn(Optional.of(existingAccount));

        assertThatThrownBy(() -> accountService.debit(accountId, Money.brl(new BigDecimal("50.00"))))
                .isInstanceOf(InsufficientFundsException.class);

        verify(repository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("credit should increase the balance and save the account")
    void creditShouldIncreaseTheBalanceAndSaveTheAccount() {
        accountService = newService();
        Account existingAccount = new Account("Kauan Brianez", Money.brl(new BigDecimal("100.00")));
        when(repository.findById(accountId)).thenReturn(Optional.of(existingAccount));
        when(repository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.credit(accountId, Money.brl(new BigDecimal("25.00")));

        assertThat(existingAccount.getBalance()).isEqualTo(Money.brl(new BigDecimal("125.00")));
        verify(repository, times(1)).save(existingAccount);
    }

    @Test
    @DisplayName("credit should throw AccountNotFoundException when the account does not exist")
    void creditShouldThrowAccountNotFoundExceptionWhenTheAccountDoesNotExist() {
        accountService = newService();
        when(repository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.credit(accountId, Money.brl(new BigDecimal("10.00"))))
                .isInstanceOf(AccountNotFoundException.class);

        verify(repository, never()).save(any(Account.class));
    }
}