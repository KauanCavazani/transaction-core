package com.transactioncore.paymentservice.service;

import com.transactioncore.paymentservice.client.AccountClient;
import com.transactioncore.paymentservice.client.dto.AccountAvailability;
import com.transactioncore.paymentservice.domain.Transaction;
import com.transactioncore.paymentservice.messaging.OutboxEventPublisher;
import com.transactioncore.paymentservice.repository.TransactionRepository;
import com.transactioncore.shared.events.DomainEvent;
import com.transactioncore.shared.events.TransactionInitiatedEvent;
import com.transactioncore.shared.exceptions.AccountNotFoundException;
import com.transactioncore.shared.exceptions.AccountNotOperationalException;
import com.transactioncore.shared.exceptions.DuplicateTransactionException;
import com.transactioncore.shared.valueobject.IdempotencyKey;
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
class TransactionProcessorTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountClient accountClient;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private TransactionProcessor transactionProcessor;

    private final UUID sourceAccountId = UUID.randomUUID();
    private final UUID destinationAccountId = UUID.randomUUID();
    private final Money amount = Money.brl(new BigDecimal("150.00"));
    private final IdempotencyKey idempotencyKey = IdempotencyKey.of("key-1");

    private TransactionProcessor newProcessor() {
        return new TransactionProcessor(transactionRepository, accountClient, outboxEventPublisher);
    }

    @Test
    @DisplayName("processTransfer should create, save and publish the event when both accounts are active")
    void processTransferShouldCreateSaveAndPublishTheEventWhenBothAccountsAreActive() {
        transactionProcessor = newProcessor();
        when(transactionRepository.findByIdempotencyKey(idempotencyKey.value())).thenReturn(Optional.empty());
        when(accountClient.checkAvailability(sourceAccountId.toString())).thenReturn(AccountAvailability.ACTIVE);
        when(accountClient.checkAvailability(destinationAccountId.toString())).thenReturn(AccountAvailability.ACTIVE);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction result = transactionProcessor.processTransfer(idempotencyKey, sourceAccountId, destinationAccountId, amount);

        assertThat(result).isNotNull();
        assertThat(result.getSourceAccountId()).isEqualTo(sourceAccountId);
        assertThat(result.getDestinationAccountId()).isEqualTo(destinationAccountId);
        assertThat(result.getAmount()).isEqualTo(amount);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("processTransfer should publish a TransactionInitiatedEvent matching the created transaction")
    void processTransferShouldPublishATransactionInitiatedEventMatchingTheCreatedTransaction() {
        transactionProcessor = newProcessor();
        when(transactionRepository.findByIdempotencyKey(idempotencyKey.value())).thenReturn(Optional.empty());
        when(accountClient.checkAvailability(sourceAccountId.toString())).thenReturn(AccountAvailability.ACTIVE);
        when(accountClient.checkAvailability(destinationAccountId.toString())).thenReturn(AccountAvailability.ACTIVE);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction result = transactionProcessor.processTransfer(idempotencyKey, sourceAccountId, destinationAccountId, amount);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outboxEventPublisher, times(1)).publish(eventCaptor.capture());

        DomainEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent).isInstanceOf(TransactionInitiatedEvent.class);
        assertThat(publishedEvent.transactionId()).isEqualTo(result.getId());
    }

    @Test
    @DisplayName("processTransfer should return the existing transaction when the data matches")
    void processTransferShouldReturnTheExistingTransactionWhenTheDataMatches() {
        transactionProcessor = newProcessor();
        Transaction existingTransaction = new Transaction(idempotencyKey.value(), sourceAccountId, destinationAccountId, amount);
        when(transactionRepository.findByIdempotencyKey(idempotencyKey.value())).thenReturn(Optional.of(existingTransaction));

        Transaction result = transactionProcessor.processTransfer(idempotencyKey, sourceAccountId, destinationAccountId, amount);

        assertThat(result).isEqualTo(existingTransaction);
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(accountClient, never()).checkAvailability(any());
        verify(outboxEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("processTransfer should throw DuplicateTransactionException when the same key is reused with different data")
    void processTransferShouldThrowDuplicateTransactionExceptionWhenTheSameKeyIsReusedWithDifferentData() {
        transactionProcessor = newProcessor();
        Transaction existingTransaction = new Transaction(idempotencyKey.value(), sourceAccountId, destinationAccountId, amount);
        when(transactionRepository.findByIdempotencyKey(idempotencyKey.value())).thenReturn(Optional.of(existingTransaction));
        Money differentAmount = Money.brl(new BigDecimal("999.00"));

        assertThatThrownBy(() -> transactionProcessor.processTransfer(idempotencyKey, sourceAccountId, destinationAccountId, differentAmount))
                .isInstanceOf(DuplicateTransactionException.class);

        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(outboxEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("processTransfer should throw AccountNotFoundException when the source account does not exist")
    void processTransferShouldThrowAccountNotFoundExceptionWhenTheSourceAccountDoesNotExist() {
        transactionProcessor = newProcessor();
        when(transactionRepository.findByIdempotencyKey(idempotencyKey.value())).thenReturn(Optional.empty());
        when(accountClient.checkAvailability(sourceAccountId.toString())).thenReturn(AccountAvailability.NOT_FOUND);

        assertThatThrownBy(() -> transactionProcessor.processTransfer(idempotencyKey, sourceAccountId, destinationAccountId, amount))
                .isInstanceOf(AccountNotFoundException.class);

        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(outboxEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("processTransfer should throw AccountNotOperationalException when the source account is inactive")
    void processTransferShouldThrowAccountNotOperationalExceptionWhenTheSourceAccountIsInactive() {
        transactionProcessor = newProcessor();
        when(transactionRepository.findByIdempotencyKey(idempotencyKey.value())).thenReturn(Optional.empty());
        when(accountClient.checkAvailability(sourceAccountId.toString())).thenReturn(AccountAvailability.INACTIVE);

        assertThatThrownBy(() -> transactionProcessor.processTransfer(idempotencyKey, sourceAccountId, destinationAccountId, amount))
                .isInstanceOf(AccountNotOperationalException.class);

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("processTransfer should throw AccountNotFoundException when the destination account does not exist")
    void processTransferShouldThrowAccountNotFoundExceptionWhenTheDestinationAccountDoesNotExist() {
        transactionProcessor = newProcessor();
        when(transactionRepository.findByIdempotencyKey(idempotencyKey.value())).thenReturn(Optional.empty());
        when(accountClient.checkAvailability(sourceAccountId.toString())).thenReturn(AccountAvailability.ACTIVE);
        when(accountClient.checkAvailability(destinationAccountId.toString())).thenReturn(AccountAvailability.NOT_FOUND);

        assertThatThrownBy(() -> transactionProcessor.processTransfer(idempotencyKey, sourceAccountId, destinationAccountId, amount))
                .isInstanceOf(AccountNotFoundException.class);

        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(outboxEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("processTransfer should not check the destination account when the source account is already invalid")
    void processTransferShouldNotCheckTheDestinationAccountWhenTheSourceAccountIsAlreadyInvalid() {
        transactionProcessor = newProcessor();
        when(transactionRepository.findByIdempotencyKey(idempotencyKey.value())).thenReturn(Optional.empty());
        when(accountClient.checkAvailability(sourceAccountId.toString())).thenReturn(AccountAvailability.NOT_FOUND);

        assertThatThrownBy(() -> transactionProcessor.processTransfer(idempotencyKey, sourceAccountId, destinationAccountId, amount))
                .isInstanceOf(AccountNotFoundException.class);

        verify(accountClient, never()).checkAvailability(destinationAccountId.toString());
    }
}