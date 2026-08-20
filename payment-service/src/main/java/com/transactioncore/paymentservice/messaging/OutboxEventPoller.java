package com.transactioncore.paymentservice.messaging;

import com.transactioncore.paymentservice.domain.OutboxEvent;
import com.transactioncore.paymentservice.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.logging.Logger;

@Component
public class OutboxEventPoller {

    private static final Logger logger = Logger.getLogger(OutboxEventPoller.class.getName());

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxEventPoller(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = repository.findByPublishedFalse();
        for (OutboxEvent event : pendingEvents) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload()).get();
                event.markAsPublished();
                repository.save(event);
            } catch (Exception ex) {
                // Log the exception and continue with the next event
                logger.warning("Failed to publish event with ID " + event.getId() + ": " + ex.getMessage());
            }
        }
    }

}
