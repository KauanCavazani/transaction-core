package com.transactioncore.notificationservice.messaging;

import com.transactioncore.notificationservice.domain.NotificationType;
import com.transactioncore.notificationservice.service.NotificationService;
import com.transactioncore.shared.events.TransactionCompletedEvent;
import com.transactioncore.shared.events.TransactionFailedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationEventListener listener;

    private NotificationEventListener newListener() {
        return new NotificationEventListener(notificationService);
    }

    @Test
    @DisplayName("handleCompleted should notify with TRANSACTION_COMPLETED and the event's ids")
    void handleCompletedShouldNotifyWithTransactionSuccessAndTheEventsIds() {
        listener = newListener();
        UUID transactionId = UUID.randomUUID();
        TransactionCompletedEvent event = TransactionCompletedEvent.create(transactionId);

        listener.handleCompleted(event);

        ArgumentCaptor<UUID> eventIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> transactionIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<NotificationType> typeCaptor = ArgumentCaptor.forClass(NotificationType.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        verify(notificationService, times(1)).notify(
                eventIdCaptor.capture(),
                transactionIdCaptor.capture(),
                typeCaptor.capture(),
                messageCaptor.capture()
        );

        assertThat(eventIdCaptor.getValue()).isEqualTo(event.eventId());
        assertThat(transactionIdCaptor.getValue()).isEqualTo(transactionId);
        assertThat(typeCaptor.getValue()).isEqualTo(NotificationType.TRANSACTION_COMPLETED);
        assertThat(messageCaptor.getValue()).isNotBlank();
    }

    @Test
    @DisplayName("handleFailed should notify with TRANSACTION_FAILED and the event's ids")
    void handleFailedShouldNotifyWithTransactionFailureAndTheEventsIds() {
        listener = newListener();
        UUID transactionId = UUID.randomUUID();
        TransactionFailedEvent event = TransactionFailedEvent.create(
                transactionId, "INSUFFICIENT_FUNDS", "Debit operation failed with result: INSUFFICIENT_FUNDS");

        listener.handleFailed(event);

        ArgumentCaptor<UUID> eventIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> transactionIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<NotificationType> typeCaptor = ArgumentCaptor.forClass(NotificationType.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        verify(notificationService, times(1)).notify(
                eventIdCaptor.capture(),
                transactionIdCaptor.capture(),
                typeCaptor.capture(),
                messageCaptor.capture()
        );

        assertThat(eventIdCaptor.getValue()).isEqualTo(event.eventId());
        assertThat(transactionIdCaptor.getValue()).isEqualTo(transactionId);
        assertThat(typeCaptor.getValue()).isEqualTo(NotificationType.TRANSACTION_FAILED);
    }

    @Test
    @DisplayName("handleFailed should include the reason code and reason message in the notification text")
    void handleFailedShouldIncludeTheReasonCodeAndReasonMessageInTheNotificationText() {
        listener = newListener();
        UUID transactionId = UUID.randomUUID();
        TransactionFailedEvent event = TransactionFailedEvent.create(
                transactionId, "INSUFFICIENT_FUNDS", "Debit operation failed with result: INSUFFICIENT_FUNDS");

        listener.handleFailed(event);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notify(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                messageCaptor.capture()
        );

        assertThat(messageCaptor.getValue())
                .contains("INSUFFICIENT_FUNDS")
                .contains("Debit operation failed with result: INSUFFICIENT_FUNDS");
    }
}