package com.transactioncore.paymentservice.domain;

import com.transactioncore.shared.valueobject.IdempotencyKey;
import com.transactioncore.shared.valueobject.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionTest {

    private final UUID sourceAccountId = UUID.randomUUID();
    private final UUID destinationAccountId = UUID.randomUUID();
    private final Money amount = Money.brl(new BigDecimal("150.00"));

    @Test
    @DisplayName("a new transaction should start PENDING with the given data")
    void aNewTransactionShouldStartPendingWithTheGivenData() {
        Transaction transaction = new Transaction("key-1", sourceAccountId, destinationAccountId, amount);

        assertThat(transaction.getId()).isNotNull();
        assertThat(transaction.getIdempotencyKey()).isEqualTo(IdempotencyKey.of("key-1"));
        assertThat(transaction.getSourceAccountId()).isEqualTo(sourceAccountId);
        assertThat(transaction.getDestinationAccountId()).isEqualTo(destinationAccountId);
        assertThat(transaction.getAmount()).isEqualTo(amount);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transaction.getFailureReasonCode()).isNull();
        assertThat(transaction.getCreatedAt()).isNotNull();
        assertThat(transaction.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("markAsCompleted should change status to COMPLETED")
    void markAsCompletedShouldChangeStatusToCompleted() {
        Transaction transaction = new Transaction("key-1", sourceAccountId, destinationAccountId, amount);

        transaction.markAsCompleted();

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("markAsFailed should change status to FAILED and store the reason code")
    void markAsFailedShouldChangeStatusToFailedAndStoreTheReasonCode() {
        Transaction transaction = new Transaction("key-1", sourceAccountId, destinationAccountId, amount);

        transaction.markAsFailed("INSUFFICIENT_FUNDS");

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transaction.getFailureReasonCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    @DisplayName("matches should return true when all three fields are the same")
    void matchesShouldReturnTrueWhenAllThreeFieldsAreTheSame() {
        Transaction transaction = new Transaction("key-1", sourceAccountId, destinationAccountId, amount);

        boolean result = transaction.matches(sourceAccountId, destinationAccountId, amount);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("matches should return true even when the amount has a different scale")
    void matchesShouldReturnTrueEvenWhenTheAmountHasADifferentScale() {
        Transaction transaction = new Transaction("key-1", sourceAccountId, destinationAccountId, amount);
        Money sameValueDifferentScale = Money.brl(new BigDecimal("150.0"));

        boolean result = transaction.matches(sourceAccountId, destinationAccountId, sameValueDifferentScale);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("matches should return false when the source account is different")
    void matchesShouldReturnFalseWhenTheSourceAccountIsDifferent() {
        Transaction transaction = new Transaction("key-1", sourceAccountId, destinationAccountId, amount);

        boolean result = transaction.matches(UUID.randomUUID(), destinationAccountId, amount);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("matches should return false when the destination account is different")
    void matchesShouldReturnFalseWhenTheDestinationAccountIsDifferent() {
        Transaction transaction = new Transaction("key-1", sourceAccountId, destinationAccountId, amount);

        boolean result = transaction.matches(sourceAccountId, UUID.randomUUID(), amount);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("matches should return false when the amount is different")
    void matchesShouldReturnFalseWhenTheAmountIsDifferent() {
        Transaction transaction = new Transaction("key-1", sourceAccountId, destinationAccountId, amount);
        Money differentAmount = Money.brl(new BigDecimal("999.00"));

        boolean result = transaction.matches(sourceAccountId, destinationAccountId, differentAmount);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("matches should return false when the currency is different")
    void matchesShouldReturnFalseWhenTheCurrencyIsDifferent() {
        Transaction transaction = new Transaction("key-1", sourceAccountId, destinationAccountId, amount);
        Money sameValueDifferentCurrency = Money.of(new BigDecimal("150.00"), "USD");

        boolean result = transaction.matches(sourceAccountId, destinationAccountId, sameValueDifferentCurrency);

        assertThat(result).isFalse();
    }
}