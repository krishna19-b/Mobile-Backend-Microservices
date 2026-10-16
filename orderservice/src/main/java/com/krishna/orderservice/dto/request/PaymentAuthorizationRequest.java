package com.krishna.orderservice.dto.request;

import java.math.BigDecimal;

public record PaymentAuthorizationRequest(
        Long orderId,
        Long userId,
        BigDecimal amount,
        boolean simulateFailure
) {
}