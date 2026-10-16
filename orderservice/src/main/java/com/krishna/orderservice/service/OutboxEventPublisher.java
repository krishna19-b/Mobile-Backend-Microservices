package com.krishna.orderservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krishna.orderservice.entity.OutboxEvent;
import com.krishna.orderservice.entity.OutboxEventStatus;
import com.krishna.orderservice.event.OrderCreatedEvent;
import com.krishna.orderservice.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, Object> kafkaTemplate, ObjectMapper objectMapper) {

        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {

        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxEventStatus.PENDING.name());

        for (OutboxEvent outboxEvent : pendingEvents) {

            try {

                JsonNode jsonNode = objectMapper.readTree(outboxEvent.getPayload());

                OrderCreatedEvent event = objectMapper.treeToValue(jsonNode, OrderCreatedEvent.class);

                kafkaTemplate.send(outboxEvent.getTopic(), event).get();

                outboxEvent.setStatus(OutboxEventStatus.PUBLISHED.name());

                outboxEvent.setPublishedAt(LocalDateTime.now());

                outboxEventRepository.save(outboxEvent);

                System.out.println("Outbox event published successfully: " + outboxEvent.getEventId());

            } catch (Exception e) {

                System.out.println("Failed to publish outbox event: " + outboxEvent.getEventId());

                System.out.println("Event remains PENDING.");
            }
        }
    }
}