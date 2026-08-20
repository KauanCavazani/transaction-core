package com.transactioncore.accountservice.domain;

import com.transactioncore.shared.exceptions.InsufficientFundsException;
import com.transactioncore.shared.valueobject.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @Test
    @DisplayName("a new account should start ACTIVE with the given owner name and initial balance")
    void aNewAccountShouldStartActiveWithTheGivenOwnerNameAndInitialBalance() {
        Money initialBalance = Money.brl(new BigDecimal("500.00"));

        Account account = new Account("Kauan Brianez", initialBalance);

        assertThat(account.getId()).isNotNull();
        assertThat(account.getOwnerName()).isEqualTo("Kauan Brianez");
        assertThat(account.getBalance()).isEqualTo(initialBalance);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getCreatedAt()).isNotNull();
        assertThat(account.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("a new account can be created with a zero initial balance")
    void aNewAccountCanBeCreatedWithAZeroInitialBalance() {
        Money zero = Money.zero(Currency.getInstance("BRL"));

        Account account = new Account("Kauan Brianez", zero);

        assertThat(account.getBalance().isZero()).isTrue();
    }

    @Test
    @DisplayName("debit should reduce the balance by the given amount")
    void debitShouldReduceTheBalanceByTheGivenAmount() {
        Account account = new Account("Kauan Brianez", Money.brl(new BigDecimal("100.00")));

        account.debit(Money.brl(new BigDecimal("30.00")));

        assertThat(account.getBalance()).isEqualTo(Money.brl(new BigDecimal("70.00")));
    }

    @Test
    @DisplayName("debit should throw InsufficientFundsException when amount is greater than the balance")
    void debitShouldThrowInsufficientFundsExceptionWhenAmountIsGreaterThanTheBalance() {
        Account account = new Account("Kauan Brianez", Money.brl(new BigDecimal("10.00")));

        assertThatThrownBy(() -> account.debit(Money.brl(new BigDecimal("50.00"))))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    @DisplayName("debit should not change the balance when it throws InsufficientFundsException")
    void debitShouldNotChangeTheBalanceWhenItThrowsInsufficientFundsException() {
        Account account = new Account("Kauan Brianez", Money.brl(new BigDecimal("10.00")));

        assertThatThrownBy(() -> account.debit(Money.brl(new BigDecimal("50.00"))))
                .isInstanceOf(InsufficientFundsException.class);

        // Garante que uma tentativa de débito que falha não deixa o saldo
        // em um estado parcialmente alterado.
        assertThat(account.getBalance()).isEqualTo(Money.brl(new BigDecimal("10.00")));
    }

    @Test
    @DisplayName("debit of the exact full balance should zero the account instead of throwing")
    void debitOfTheExactFullBalanceShouldZeroTheAccountInsteadOfThrowing() {
        Account account = new Account("Kauan Brianez", Money.brl(new BigDecimal("100.00")));

        account.debit(Money.brl(new BigDecimal("100.00")));

        assertThat(account.getBalance().isZero()).isTrue();
    }

    @Test
    @DisplayName("debit with a different currency should throw IllegalArgumentException")
    void debitWithADifferentCurrencyShouldThrowIllegalArgumentException() {
        Account account = new Account("Kauan Brianez", Money.brl(new BigDecimal("100.00")));
        Money amountInDollars = Money.of(new BigDecimal("10.00"), "USD");

        assertThatThrownBy(() -> account.debit(amountInDollars))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("credit should increase the balance by the given amount")
    void creditShouldIncreaseTheBalanceByTheGivenAmount() {
        Account account = new Account("Kauan Brianez", Money.brl(new BigDecimal("100.00")));

        account.credit(Money.brl(new BigDecimal("25.00")));

        assertThat(account.getBalance()).isEqualTo(Money.brl(new BigDecimal("125.00")));
    }

    @Test
    @DisplayName("credit with a different currency should throw IllegalArgumentException")
    void creditWithADifferentCurrencyShouldThrowIllegalArgumentException() {
        Account account = new Account("Kauan Brianez", Money.brl(new BigDecimal("100.00")));
        Money amountInDollars = Money.of(new BigDecimal("10.00"), "USD");

        assertThatThrownBy(() -> account.credit(amountInDollars))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("debit followed by credit of the same amount should return to the original balance")
    void debitFollowedByCreditOfTheSameAmountShouldReturnToTheOriginalBalance() {
        Account account = new Account("Kauan Brianez", Money.brl(new BigDecimal("100.00")));

        account.debit(Money.brl(new BigDecimal("40.00")));
        account.credit(Money.brl(new BigDecimal("40.00")));

        assertThat(account.getBalance()).isEqualTo(Money.brl(new BigDecimal("100.00")));
    }
}