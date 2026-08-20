package com.transactioncore.shared.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    @DisplayName("should add two amounts in the same currency")
    void shouldAddTwoAmountsInTheSameCurrency() {
        Money a = Money.brl(new BigDecimal("10.50"));
        Money b = Money.brl(new BigDecimal("5.25"));

        Money result = a.add(b);

        assertThat(result).isEqualTo(Money.brl(new BigDecimal("15.75")));
    }

    @Test
    @DisplayName("should subtract two amounts in the same currency")
    void shouldSubtractTwoAmountsInTheSameCurrency() {
        Money a = Money.brl(new BigDecimal("10.00"));
        Money b = Money.brl(new BigDecimal("3.00"));

        Money result = a.subtract(b);

        assertThat(result).isEqualTo(Money.brl(new BigDecimal("7.00")));
    }

    @Test
    @DisplayName("should not allow adding amounts with different currencies")
    void shouldNotAllowAddingAmountsWithDifferentCurrencies() {
        Money brl = Money.brl(new BigDecimal("10.00"));
        Money usd = Money.of(new BigDecimal("10.00"), "USD");

        assertThatThrownBy(() -> brl.add(usd))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should not allow subtracting amounts with different currencies")
    void shouldNotAllowSubtractingAmountsWithDifferentCurrencies() {
        Money brl = Money.brl(new BigDecimal("10.00"));
        Money usd = Money.of(new BigDecimal("10.00"), "USD");

        assertThatThrownBy(() -> brl.subtract(usd))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should identify a negative amount")
    void shouldIdentifyANegativeAmount() {
        Money amount = Money.brl(new BigDecimal("-5.00"));

        assertThat(amount.isNegative()).isTrue();
    }

    @Test
    @DisplayName("should not identify a positive amount as negative")
    void shouldNotIdentifyAPositiveAmountAsNegative() {
        Money amount = Money.brl(new BigDecimal("5.00"));

        assertThat(amount.isNegative()).isFalse();
    }

    @Test
    @DisplayName("should identify a zero amount")
    void shouldIdentifyAZeroAmount() {
        Money amount = Money.zero(Currency.getInstance("BRL"));

        assertThat(amount.isZero()).isTrue();
    }

    @Test
    @DisplayName("isGreaterThanOrEqualTo should return true for equal amounts")
    void isGreaterThanOrEqualToShouldReturnTrueForEqualAmounts() {
        Money a = Money.brl(new BigDecimal("100.00"));
        Money b = Money.brl(new BigDecimal("100.00"));

        assertThat(a.isGreaterThanOrEqualTo(b)).isTrue();
    }

    @Test
    @DisplayName("isGreaterThanOrEqualTo should return false when the amount is smaller")
    void isGreaterThanOrEqualToShouldReturnFalseWhenTheAmountIsSmaller() {
        Money balance = Money.brl(new BigDecimal("50.00"));
        Money transferAmount = Money.brl(new BigDecimal("100.00"));

        assertThat(balance.isGreaterThanOrEqualTo(transferAmount)).isFalse();
    }

    @Test
    @DisplayName("should throw an exception when amount is null")
    void shouldThrowAnExceptionWhenAmountIsNull() {
        assertThatThrownBy(() -> new Money(null, Currency.getInstance("BRL")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should throw an exception when currency is null")
    void shouldThrowAnExceptionWhenCurrencyIsNull() {
        assertThatThrownBy(() -> new Money(BigDecimal.TEN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}