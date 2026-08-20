package com.transactioncore.ledgerservice.messaging;

import com.transactioncore.ledgerservice.domain.OutboxEvent;
import com.transactioncore.ledgerservice.repository.OutboxEventRepository;
import com.transactioncore.shared.events.DomainEvent;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class OutboxEventPublisher {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void publish(DomainEvent event) {
        try {
            String eventData = objectMapper.writeValueAsString(event);
            var outboxEvent = new OutboxEvent(
                    event.transactionId(),
                    event.getClass().getSimpleName(),
                    event.topic(),
                    eventData
            );
            repository.save(outboxEvent);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to publish event", e);
        }
    }

}
