package com.krishna.orderservice.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderCancelledEventContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldSerializeAllRequiredEventContractFields() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        LocalDateTime occurredAt = LocalDateTime.now();

        OrderCancelledEvent event = new OrderCancelledEvent(
                eventId,
                "ORDER_CANCELLED",
                1,
                36L,
                1L,
                occurredAt,
                correlationId
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode payload = objectMapper.readTree(json);

        assertEquals(eventId, payload.get("eventId").asText());
        assertEquals("ORDER_CANCELLED", payload.get("eventType").asText());
        assertEquals(1, payload.get("schemaVersion").asInt());
        assertEquals(36L, payload.get("orderId").asLong());
        assertEquals(1L, payload.get("userId").asLong());

        assertTrue(payload.hasNonNull("occurredAt"));
        assertEquals(
                occurredAt,
                LocalDateTime.parse(payload.get("occurredAt").asText())
        );

        assertEquals(correlationId, payload.get("correlationId").asText());
    }

    @Test
    void shouldContainAllRequiredContractFields() throws Exception {
        OrderCancelledEvent event = new OrderCancelledEvent(
                UUID.randomUUID().toString(),
                "ORDER_CANCELLED",
                1,
                36L,
                1L,
                LocalDateTime.now(),
                UUID.randomUUID().toString()
        );

        JsonNode payload = objectMapper.readTree(
                objectMapper.writeValueAsString(event)
        );

        assertTrue(payload.hasNonNull("eventId"));
        assertTrue(payload.hasNonNull("eventType"));
        assertTrue(payload.hasNonNull("schemaVersion"));
        assertTrue(payload.hasNonNull("orderId"));
        assertTrue(payload.hasNonNull("userId"));
        assertTrue(payload.hasNonNull("occurredAt"));
        assertTrue(payload.hasNonNull("correlationId"));
    }
}