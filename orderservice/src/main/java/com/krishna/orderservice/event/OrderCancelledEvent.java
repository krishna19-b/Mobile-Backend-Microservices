package com.krishna.orderservice.event;

import java.time.LocalDateTime;

public class OrderCancelledEvent {

    private String eventId;
    private String eventType;
    private Integer schemaVersion;
    private Long orderId;
    private Long userId;
    private LocalDateTime occurredAt;
    private String correlationId;

    public OrderCancelledEvent() {
    }

    public OrderCancelledEvent(
            String eventId,
            String eventType,
            Long orderId,
            Long userId,
            LocalDateTime occurredAt) {

        this.eventId = eventId;
        this.eventType = eventType;
        this.orderId = orderId;
        this.userId = userId;
        this.occurredAt = occurredAt;
    }

    public OrderCancelledEvent(
            String eventId,
            String eventType,
            Integer schemaVersion,
            Long orderId,
            Long userId,
            LocalDateTime occurredAt,
            String correlationId) {

        this.eventId = eventId;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.orderId = orderId;
        this.userId = userId;
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}