package com.krishna.orderservice.client;

import com.krishna.orderservice.dto.request.PaymentAuthorizationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "paymentservice")
public interface PaymentClient {

    @PostMapping("/api/v1/payments/authorize")
    Map<String, Object> authorizePayment(
            @RequestBody PaymentAuthorizationRequest request);
}