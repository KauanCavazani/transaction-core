package com.transactioncore.accountservice.service;

import com.transactioncore.accountservice.domain.Account;
import com.transactioncore.accountservice.domain.ProcessedOperation;
import com.transactioncore.accountservice.repository.AccountRepository;
import com.transactioncore.accountservice.repository.ProcessedOperationRepository;
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
    private AccountRepository accountRepository;

    @Mock
    private ProcessedOperationRepository processedOperationRepository;

    private AccountService accountService;

    private final UUID accountId = UUID.randomUUID();
    private final UUID operationId = UUID.randomUUID();

    private AccountService newService() {
        return new AccountService(accountRepository, processedOperationRepository);
    }

    @Test
    @DisplayName("create should save the account and return what the repository returns")
    void createShouldSaveTheAccountAndReturnWhatTheRepositoryReturns() {
        accountService = newService();
        Money initialBalance = Money.brl(new BigDecimal("500.00"));
        Account savedAccount = new Account("Kauan Brianez", initialBalance);

        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        Account result = accountService.create("Kauan Brianez", initialBalance);

        assertThat(result).isEqualTo(savedAccount);
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    @DisplayName("findById should throw AccountNotFoundException when the account does not exist")
    void findByIdShouldThrowAccountNotFoundExceptionWhenTheAccountDoesNotExist() {
        accountService = newService();
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.findById(accountId))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("debit should reduce the balance, save the account and register the operation as processed")
    void debitShouldReduceTheBalanceSaveTheAccountAndRegisterTheOperationAsProcessed() {
        accountService = newService();
        Account existingAccount = new Account("Kauan Brianez", Money.brl(new BigDecimal("100.00")));
        when(processedOperationRepository.existsById(operationId)).thenReturn(false);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.debit(accountId, operationId, Money.brl(new BigDecimal("30.00")));

        assertThat(existingAccount.getBalance()).isEqualTo(Money.brl(new BigDecimal("70.00")));
        verify(accountRepository, times(1)).save(existingAccount);

        ArgumentCaptor<ProcessedOperation> captor = ArgumentCaptor.forClass(ProcessedOperation.class);
        verify(processedOperationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getOperationId()).isEqualTo(operationId);
    }

    @Test
    @DisplayName("debit should do nothing when the operation was already processed before")
    void debitShouldDoNothingWhenTheOperationWasAlreadyProcessedBefore() {
        accountService = newService();
        when(processedOperationRepository.existsById(operationId)).thenReturn(true);

        accountService.debit(accountId, operationId, Money.brl(new BigDecimal("30.00")));

        verify(accountRepository, never()).findById(any());
        verify(accountRepository, never()).save(any());
        verify(processedOperationRepository, never()).save(any());
    }

    @Test
    @DisplayName("debit should throw AccountNotFoundException when the account does not exist")
    void debitShouldThrowAccountNotFoundExceptionWhenTheAccountDoesNotExist() {
        accountService = newService();
        when(processedOperationRepository.existsById(operationId)).thenReturn(false);
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.debit(accountId, operationId, Money.brl(new BigDecimal("30.00"))))
                .isInstanceOf(AccountNotFoundException.class);

        verify(processedOperationRepository, never()).save(any());
    }

    @Test
    @DisplayName("debit should propagate InsufficientFundsException and not register the operation as processed")
    void debitShouldPropagateInsufficientFundsExceptionAndNotRegisterTheOperationAsProcessed() {
        accountService = newService();
        Account existingAccount = new Account("Kauan Brianez", Money.brl(new BigDecimal("10.00")));
        when(processedOperationRepository.existsById(operationId)).thenReturn(false);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount));

        assertThatThrownBy(() -> accountService.debit(accountId, operationId, Money.brl(new BigDecimal("50.00"))))
                .isInstanceOf(InsufficientFundsException.class);

        verify(accountRepository, never()).save(any());
        verify(processedOperationRepository, never()).save(any());
    }

    @Test
    @DisplayName("credit should increase the balance, save the account and register the operation as processed")
    void creditShouldIncreaseTheBalanceSaveTheAccountAndRegisterTheOperationAsProcessed() {
        accountService = newService();
        Account existingAccount = new Account("Kauan Brianez", Money.brl(new BigDecimal("100.00")));
        when(processedOperationRepository.existsById(operationId)).thenReturn(false);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.credit(accountId, operationId, Money.brl(new BigDecimal("25.00")));

        assertThat(existingAccount.getBalance()).isEqualTo(Money.brl(new BigDecimal("125.00")));
        verify(accountRepository, times(1)).save(existingAccount);
        verify(processedOperationRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("credit should do nothing when the operation was already processed before")
    void creditShouldDoNothingWhenTheOperationWasAlreadyProcessedBefore() {
        accountService = newService();
        when(processedOperationRepository.existsById(operationId)).thenReturn(true);

        accountService.credit(accountId, operationId, Money.brl(new BigDecimal("25.00")));

        verify(accountRepository, never()).findById(any());
        verify(accountRepository, never()).save(any());
        verify(processedOperationRepository, never()).save(any());
    }

    @Test
    @DisplayName("credit should throw AccountNotFoundException when the account does not exist")
    void creditShouldThrowAccountNotFoundExceptionWhenTheAccountDoesNotExist() {
        accountService = newService();
        when(processedOperationRepository.existsById(operationId)).thenReturn(false);
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.credit(accountId, operationId, Money.brl(new BigDecimal("25.00"))))
                .isInstanceOf(AccountNotFoundException.class);

        verify(processedOperationRepository, never()).save(any());
    }

    @Test
    @DisplayName("calling debit twice with the same operationId should only apply the effect once")
    void callingDebitTwiceWithTheSameOperationIdShouldOnlyApplyTheEffectOnce() {
        accountService = newService();
        Account existingAccount = new Account("Kauan Brianez", Money.brl(new BigDecimal("100.00")));

        when(processedOperationRepository.existsById(operationId)).thenReturn(false);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.debit(accountId, operationId, Money.brl(new BigDecimal("30.00")));
        assertThat(existingAccount.getBalance()).isEqualTo(Money.brl(new BigDecimal("70.00")));

        when(processedOperationRepository.existsById(operationId)).thenReturn(true);

        accountService.debit(accountId, operationId, Money.brl(new BigDecimal("30.00")));

        assertThat(existingAccount.getBalance()).isEqualTo(Money.brl(new BigDecimal("70.00")));
        verify(accountRepository, times(1)).save(existingAccount);
    }
}