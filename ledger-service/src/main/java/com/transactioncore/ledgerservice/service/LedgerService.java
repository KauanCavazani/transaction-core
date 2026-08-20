package com.transactioncore.ledgerservice.service;

import com.transactioncore.ledgerservice.client.AccountClient;
import com.transactioncore.ledgerservice.client.dto.OperationResult;
import com.transactioncore.ledgerservice.domain.LedgerEntry;
import com.transactioncore.ledgerservice.domain.LedgerEntryType;
import com.transactioncore.ledgerservice.domain.ProcessedEvent;
import com.transactioncore.ledgerservice.messaging.OutboxEventPublisher;
import com.transactioncore.ledgerservice.repository.LedgerEntryRepository;
import com.transactioncore.ledgerservice.repository.ProcessedEventRepository;
import com.transactioncore.shared.events.TransactionCompletedEvent;
import com.transactioncore.shared.events.TransactionFailedEvent;
import com.transactioncore.shared.valueobject.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.logging.Logger;

@Service
public class LedgerService {

    //logger
    private static final java.util.logging.Logger logger = Logger.getLogger(LedgerService.class.getName());

    private final LedgerEntryRepository repository;
    private final ProcessedEventRepository processedEventRepository;
    private final AccountClient accountClient;
    private final OutboxEventPublisher outboxEventPublisher;

    public LedgerService(
            LedgerEntryRepository repository,
            ProcessedEventRepository processedEventRepository,
            AccountClient accountClient,
            OutboxEventPublisher outboxEventPublisher
    ) {
        this.repository = repository;
        this.processedEventRepository = processedEventRepository;
        this.accountClient = accountClient;
        this.outboxEventPublisher = outboxEventPublisher;
    }

    @Transactional
    public void processTransaction(UUID eventId, UUID transactionId, UUID sourceAccountId, UUID destinationAccountId, Money amount) {
        if (processedEventRepository.existsById(eventId)) return;

        UUID debitOperationId = UUID.nameUUIDFromBytes((transactionId + "-debit").getBytes());
        OperationResult debitResult = accountClient.debit(sourceAccountId, debitOperationId, amount);

        if (debitResult != OperationResult.SUCCESS) {
            publishFailure(transactionId, debitResult, "Debit");
            return;
        }

        UUID creditOperationId = UUID.nameUUIDFromBytes((transactionId + "-credit").getBytes());
        OperationResult creditResult = accountClient.credit(destinationAccountId, creditOperationId, amount);

        if (creditResult != OperationResult.SUCCESS) {
            compensateDebit(transactionId, sourceAccountId, amount);
            publishFailure(transactionId, creditResult, "Credit");
            return;
        }

        recordSuccess(eventId, transactionId, sourceAccountId, destinationAccountId, amount);
    }

    private void publishFailure(UUID transactionId, OperationResult result, String context) {
        String reasonCode = result.toString();
        String reasonMessage = context + " operation failed with result: " + result;
        TransactionFailedEvent event = TransactionFailedEvent.create(transactionId, reasonCode, reasonMessage);
        outboxEventPublisher.publish(event);
    }

    private void compensateDebit(UUID transactionId, UUID sourceAccountId, Money amount) {
        UUID rollbackOperationId = UUID.nameUUIDFromBytes((transactionId + "-rollback").getBytes());
        OperationResult compensationResult = accountClient.credit(sourceAccountId, rollbackOperationId, amount);
        if (compensationResult != OperationResult.SUCCESS) {
            logger.severe("Compensation failed for transaction " + transactionId
                    + ", account " + sourceAccountId
                    + ", amount " + amount
                    + ". Result: " + compensationResult + ". Manual reconciliation required.");
        }
    }

    private void recordSuccess(UUID eventId, UUID transactionId, UUID sourceAccountId, UUID destinationAccountId, Money amount) {
        LedgerEntry debitEntry = new LedgerEntry(transactionId, sourceAccountId, LedgerEntryType.DEBIT, amount);
        LedgerEntry creditEntry = new LedgerEntry(transactionId, destinationAccountId, LedgerEntryType.CREDIT, amount);
        repository.save(debitEntry);
        repository.save(creditEntry);

        TransactionCompletedEvent event = TransactionCompletedEvent.create(transactionId);
        outboxEventPublisher.publish(event);

        ProcessedEvent processedEvent = new ProcessedEvent(eventId);
        processedEventRepository.save(processedEvent);
    }

}
