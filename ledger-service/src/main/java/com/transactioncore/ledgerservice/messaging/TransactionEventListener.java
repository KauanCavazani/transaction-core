package com.transactioncore.ledgerservice.messaging;

import com.transactioncore.ledgerservice.service.LedgerService;
import com.transactioncore.shared.events.TransactionInitiatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventListener {

    private final LedgerService ledgerService;

    public TransactionEventListener(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @KafkaListener(topics = TransactionInitiatedEvent.TOPIC, groupId = "ledger-service")
    public void handle(TransactionInitiatedEvent event) {
        ledgerService.processTransaction(
                event.eventId(),
                event.transactionId(),
                event.sourceAccountId(),
                event.destinationAccountId(),
                event.amount()
        );
    }

}
