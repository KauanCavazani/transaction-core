package com.transactioncore.notificationservice.repository;

import com.transactioncore.notificationservice.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByTransactionId(UUID transactionId);

}
