package com.transactioncore.notificationservice.messaging;

import com.transactioncore.notificationservice.domain.NotificationType;
import com.transactioncore.notificationservice.service.NotificationService;
import com.transactioncore.shared.events.TransactionCompletedEvent;
import com.transactioncore.shared.events.TransactionFailedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = TransactionCompletedEvent.TOPIC, groupId = "notification-service", containerFactory = "transactionCompletedListenerFactory", concurrency = "3")
    public void handleCompleted(TransactionCompletedEvent event) {
        notificationService.notify(
                event.eventId(),
                event.transactionId(),
                NotificationType.TRANSACTION_COMPLETED,
                "Transaction completed successfully."
        );
    }

    @KafkaListener(topics = TransactionFailedEvent.TOPIC, groupId = "notification-service", containerFactory = "transactionFailedListenerFactory", concurrency = "3")
    public void handleFailed(TransactionFailedEvent event) {
        notificationService.notify(
                event.eventId(),
                event.transactionId(),
                NotificationType.TRANSACTION_FAILED,
                "Transaction failed: " + event.reasonCode() + " - " + event.reasonMessage()
        );
    }

}
