package com.transactioncore.notificationservice.service;

import com.transactioncore.notificationservice.domain.Notification;
import com.transactioncore.notificationservice.domain.NotificationType;
import com.transactioncore.notificationservice.domain.ProcessedEvent;
import com.transactioncore.notificationservice.repository.NotificationRepository;
import com.transactioncore.notificationservice.repository.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final ProcessedEventRepository processedEventRepository;

    public NotificationService(NotificationRepository repository, ProcessedEventRepository processedEventRepository) {
        this.repository = repository;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public void notify(UUID eventId, UUID transactionId, NotificationType type, String message) {
        if (processedEventRepository.existsById(eventId)) return;
        repository.save(new Notification(transactionId, type, message));
        processedEventRepository.save(new ProcessedEvent(eventId));
    }

}
