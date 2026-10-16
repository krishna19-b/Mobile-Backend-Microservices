package com.krishna.orderservice.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderCreatedEventContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule()).disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldSerializeAllRequiredEventContractFields() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        LocalDateTime occurredAt = LocalDateTime.now();

        OrderCreatedEvent event = new OrderCreatedEvent(
                eventId,
                "ORDER_CREATED",
                1,
                36L,
                1L,
                new BigDecimal("55000.00"),
                occurredAt,
                correlationId
        );

        String json = objectMapper.writeValueAsString(event);

        // Temporary diagnostic output
        System.out.println("Serialized OrderCreatedEvent JSON: " + json);

        JsonNode payload = objectMapper.readTree(json);

        assertTrue(payload.hasNonNull("eventId"));
        assertEquals(eventId, payload.get("eventId").asText());

        assertTrue(payload.hasNonNull("eventType"));
        assertEquals("ORDER_CREATED", payload.get("eventType").asText());

        assertTrue(payload.hasNonNull("schemaVersion"));
        assertEquals(1, payload.get("schemaVersion").asInt());

        assertTrue(payload.hasNonNull("orderId"));
        assertEquals(36L, payload.get("orderId").asLong());

        assertTrue(payload.hasNonNull("userId"));
        assertEquals(1L, payload.get("userId").asLong());

        assertTrue(payload.hasNonNull("totalAmount"));
        assertEquals(
                0,
                new BigDecimal(payload.get("totalAmount").asText())
                        .compareTo(new BigDecimal("55000.00"))
        );

        assertTrue(
                payload.hasNonNull("occurredAt"),
                "occurredAt must not be null"
        );

        assertFalse(
                payload.get("occurredAt").asText().isBlank(),
                "occurredAt must not be empty"
        );

        assertEquals(
                occurredAt,
                LocalDateTime.parse(payload.get("occurredAt").asText()),
                "occurredAt must match the original event timestamp"
        );

        assertTrue(payload.hasNonNull("correlationId"));
        assertEquals(correlationId, payload.get("correlationId").asText());
    }

    @Test
    void shouldNotAllowRequiredContractFieldsToBeMissing() throws Exception {
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                "ORDER_CREATED",
                1,
                36L,
                1L,
                new BigDecimal("55000.00"),
                LocalDateTime.now(),
                UUID.randomUUID().toString()
        );

        JsonNode payload = objectMapper.readTree(
                objectMapper.writeValueAsString(event)
        );

        assertTrue(payload.hasNonNull("eventId"));
        assertTrue(payload.hasNonNull("eventType"));
        assertTrue(payload.hasNonNull("schemaVersion"));
        assertTrue(payload.hasNonNull("occurredAt"));
        assertTrue(payload.hasNonNull("correlationId"));
    }
}