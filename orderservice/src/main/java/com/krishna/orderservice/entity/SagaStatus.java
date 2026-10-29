
package com.krishna.orderservice.entity;

public enum SagaStatus {
    STARTED,
    INVENTORY_RESERVED,
    PAYMENT_PENDING,
    COMPENSATING,
    COMPLETED,
    COMPENSATION_FAILED,
    FAILED
}
