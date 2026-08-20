package com.transactioncore.ledgerservice.service;

import com.transactioncore.ledgerservice.client.AccountClient;
import com.transactioncore.ledgerservice.client.dto.OperationResult;
import com.transactioncore.ledgerservice.domain.LedgerEntry;
import com.transactioncore.ledgerservice.domain.LedgerEntryType;
import com.transactioncore.ledgerservice.domain.ProcessedEvent;
import com.transactioncore.ledgerservice.messaging.OutboxEventPublisher;
import com.transactioncore.ledgerservice.repository.LedgerEntryRepository;
import com.transactioncore.ledgerservice.repository.ProcessedEventRepository;
import com.transactioncore.shared.events.DomainEvent;
import com.transactioncore.shared.events.TransactionCompletedEvent;
import com.transactioncore.shared.events.TransactionFailedEvent;
import com.transactioncore.shared.valueobject.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private AccountClient accountClient;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private LedgerService ledgerService;

    private final UUID eventId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();
    private final UUID sourceAccountId = UUID.randomUUID();
    private final UUID destinationAccountId = UUID.randomUUID();
    private final Money amount = Money.brl(new BigDecimal("150.00"));

    private LedgerService newService() {
        return new LedgerService(ledgerEntryRepository, processedEventRepository, accountClient, outboxEventPublisher);
    }

    @Test
    @DisplayName("processTransaction should do nothing when the event was already processed before")
    void processTransactionShouldDoNothingWhenTheEventWasAlreadyProcessedBefore() {
        ledgerService = newService();
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        ledgerService.processTransaction(eventId, transactionId, sourceAccountId, destinationAccountId, amount);

        verify(accountClient, never()).debit(any(), any(), any());
        verify(accountClient, never()).credit(any(), any(), any());
        verify(outboxEventPublisher, never()).publish(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("processTransaction should create both ledger entries when debit and credit both succeed")
    void processTransactionShouldCreateBothLedgerEntriesWhenDebitAndCreditBothSucceed() {
        ledgerService = newService();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(accountClient.debit(eq(sourceAccountId), any(), eq(amount))).thenReturn(OperationResult.SUCCESS);
        when(accountClient.credit(eq(destinationAccountId), any(), eq(amount))).thenReturn(OperationResult.SUCCESS);

        ledgerService.processTransaction(eventId, transactionId, sourceAccountId, destinationAccountId, amount);

        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(entryCaptor.capture());

        var entries = entryCaptor.getAllValues();
        assertThat(entries).hasSize(2);
        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry.getType()).isEqualTo(LedgerEntryType.DEBIT);
            assertThat(entry.getAccountId()).isEqualTo(sourceAccountId);
            assertThat(entry.getTransactionId()).isEqualTo(transactionId);
        });
        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry.getType()).isEqualTo(LedgerEntryType.CREDIT);
            assertThat(entry.getAccountId()).isEqualTo(destinationAccountId);
            assertThat(entry.getTransactionId()).isEqualTo(transactionId);
        });
    }

    @Test
    @DisplayName("processTransaction should publish a TransactionCompletedEvent when debit and credit both succeed")
    void processTransactionShouldPublishATransactionCompletedEventWhenDebitAndCreditBothSucceed() {
        ledgerService = newService();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(accountClient.debit(eq(sourceAccountId), any(), eq(amount))).thenReturn(OperationResult.SUCCESS);
        when(accountClient.credit(eq(destinationAccountId), any(), eq(amount))).thenReturn(OperationResult.SUCCESS);

        ledgerService.processTransaction(eventId, transactionId, sourceAccountId, destinationAccountId, amount);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outboxEventPublisher, times(1)).publish(eventCaptor.capture());

        assertThat(eventCaptor.getValue()).isInstanceOf(TransactionCompletedEvent.class);
        assertThat(eventCaptor.getValue().transactionId()).isEqualTo(transactionId);
    }

    @Test
    @DisplayName("processTransaction should register the event as processed when it succeeds")
    void processTransactionShouldRegisterTheEventAsProcessedWhenItSucceeds() {
        ledgerService = newService();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(accountClient.debit(eq(sourceAccountId), any(), eq(amount))).thenReturn(OperationResult.SUCCESS);
        when(accountClient.credit(eq(destinationAccountId), any(), eq(amount))).thenReturn(OperationResult.SUCCESS);

        ledgerService.processTransaction(eventId, transactionId, sourceAccountId, destinationAccountId, amount);

        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(eventId);
    }

    @Test
    @DisplayName("processTransaction should not attempt any compensation when debit and credit both succeed")
    void processTransactionShouldNotAttemptAnyCompensationWhenDebitAndCreditBothSucceed() {
        ledgerService = newService();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(accountClient.debit(eq(sourceAccountId), any(), eq(amount))).thenReturn(OperationResult.SUCCESS);
        when(accountClient.credit(eq(destinationAccountId), any(), eq(amount))).thenReturn(OperationResult.SUCCESS);

        ledgerService.processTransaction(eventId, transactionId, sourceAccountId, destinationAccountId, amount);

        // Apenas duas chamadas de crédito deveriam existir se não houve
        // compensação: nenhuma, já que o único credit() é o de destino.
        verify(accountClient, times(1)).credit(any(), any(), any());
    }

    @Test
    @DisplayName("processTransaction should publish TransactionFailedEvent and stop when debit fails")
    void processTransactionShouldPublishTransactionFailedEventAndStopWhenDebitFails() {
        ledgerService = newService();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(accountClient.debit(eq(sourceAccountId), any(), eq(amount))).thenReturn(OperationResult.INSUFFICIENT_FUNDS);

        ledgerService.processTransaction(eventId, transactionId, sourceAccountId, destinationAccountId, amount);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outboxEventPublisher, times(1)).publish(eventCaptor.capture());

        assertThat(eventCaptor.getValue()).isInstanceOf(TransactionFailedEvent.class);
        TransactionFailedEvent failedEvent = (TransactionFailedEvent) eventCaptor.getValue();
        assertThat(failedEvent.reasonCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    @DisplayName("processTransaction should never call credit when debit fails")
    void processTransactionShouldNeverCallCreditWhenDebitFails() {
        ledgerService = newService();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(accountClient.debit(eq(sourceAccountId), any(), eq(amount))).thenReturn(OperationResult.INSUFFICIENT_FUNDS);

        ledgerService.processTransaction(eventId, transactionId, sourceAccountId, destinationAccountId, amount);

        verify(accountClient, never()).credit(any(), any(), any());
    }

    @Test
    @DisplayName("processTransaction should not create ledger entries or register the event when debit fails")
    void processTransactionShouldNotCreateLedgerEntriesOrRegisterTheEventWhenDebitFails() {
        ledgerService = newService();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(accountClient.debit(eq(sourceAccountId), any(), eq(amount))).thenReturn(OperationResult.ACCOUNT_NOT_FOUND);

        ledgerService.processTransaction(eventId, transactionId, sourceAccountId, destinationAccountId, amount);

        verify(ledgerEntryRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("processTransaction should compensate the debit when credit fails")
    void processTransactionShouldCompensateTheDebitWhenCreditFails() {
        ledgerService = newService();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(accountClient.debit(eq(sourceAccountId), any(), eq(amount))).thenReturn(OperationResult.SUCCESS);
        when(accountClient.credit(eq(destinationAccountId), any(), eq(amount))).thenReturn(OperationResult.ACCOUNT_NOT_FOUND);

        ledgerService.processTransaction(eventId, transactionId, sourceAccountId, destinationAccountId, amount);

        verify(accountClient, times(2)).credit(any(), any(), eq(amount));
        verify(accountClient, times(1)).credit(eq(sourceAccountId), any(), eq(amount));
    }

    @Test
    @DisplayName("processTransaction should use a different operationId for the compensation than for the original debit")
    void processTransactionShouldUseADifferentOperationIdForTheCompensationThanForTheOriginalDebit() {
        ledgerService = newService();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(accountClient.debit(eq(sourceAccountId), any(), eq(amount))).thenReturn(OperationResult.SUCCESS);
        when(accountClient.credit(eq(destinationAccountId), any(), eq(amount))).thenReturn(OperationResult.ACCOUNT_NOT_FOUND);

        ArgumentCaptor<UUID> debitOperationIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> compensationOperationIdCaptor = ArgumentCaptor.forClass(UUID.class);

        ledgerService.processTransaction(eventId, transactionId, sourceAccountId, destinationAccountId, amount);

        verify(accountClient).debit(eq(sourceAccountId), debitOperationIdCaptor.capture(), eq(amount));
        verify(accountClient).credit(eq(sourceAccountId), compensationOperationIdCaptor.capture(), eq(amount));

        assertThat(compensationOperationIdCaptor.getValue()).isNotEqualTo(debitOperationIdCaptor.getValue());
    }

    @Test
    @DisplayName("processTransaction should publish TransactionFailedEvent when credit fails, after compensating")
    void processTransactionShouldPublishTransactionFailedEventWhenCreditFailsAfterCompensating() {
        ledgerService = newService();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(accountClient.debit(eq(sourceAccountId), any(), eq(amount))).thenReturn(OperationResult.SUCCESS);
        when(accountClient.credit(eq(destinationAccountId), any(), eq(amount))).thenReturn(OperationResult.INSUFFICIENT_FUNDS);

        ledgerService.processTransaction(eventId, transactionId, sourceAccountId, destinationAccountId, amount);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outboxEventPublisher, times(1)).publish(eventCaptor.capture());

        assertThat(eventCaptor.getValue()).isInstanceOf(TransactionFailedEvent.class);
        TransactionFailedEvent failedEvent = (TransactionFailedEvent) eventCaptor.getValue();
        assertThat(failedEvent.reasonCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    @DisplayName("processTransaction should not create ledger entries or register the event when credit fails")
    void processTransactionShouldNotCreateLedgerEntriesOrRegisterTheEventWhenCreditFails() {
        ledgerService = newService();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(accountClient.debit(eq(sourceAccountId), any(), eq(amount))).thenReturn(OperationResult.SUCCESS);
        when(accountClient.credit(eq(destinationAccountId), any(), eq(amount))).thenReturn(OperationResult.ACCOUNT_NOT_FOUND);

        ledgerService.processTransaction(eventId, transactionId, sourceAccountId, destinationAccountId, amount);

        verify(ledgerEntryRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }
}