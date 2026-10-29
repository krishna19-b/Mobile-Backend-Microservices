package com.krishna.paymentservice.controller;

import com.krishna.paymentservice.entity.Payment;
import com.krishna.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/authorize")
    public ResponseEntity<Payment> authorizePayment(
            @Valid @RequestBody PaymentRequest request) {

        Payment payment = paymentService.authorizePayment(
                request.orderId(),
                request.userId(),
                request.amount(),
                request.simulateFailure()
        );

        return ResponseEntity.ok(payment);
    }

    @PutMapping("/{orderId}/refund")
    public ResponseEntity<Payment> refundPayment(
            @PathVariable Long orderId) {

        return ResponseEntity.ok(
                paymentService.refundPayment(orderId));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Payment> getPaymentByOrderId(
            @PathVariable Long orderId) {

        return ResponseEntity.ok(
                paymentService.getPaymentByOrderId(orderId));
    }

    public record PaymentRequest(
            @NotNull @Positive Long orderId,
            @NotNull @Positive Long userId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            boolean simulateFailure) {
    }
}