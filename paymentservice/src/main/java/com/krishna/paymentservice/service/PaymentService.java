package com.krishna.paymentservice.service;

import com.krishna.paymentservice.entity.Payment;
import com.krishna.paymentservice.entity.PaymentStatus;
import com.krishna.paymentservice.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment authorizePayment(
            Long orderId,
            Long userId,
            BigDecimal amount,
            boolean simulateFailure) {

        if (orderId == null || userId == null) {
            throw new IllegalArgumentException("Order ID and user ID are required");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        Payment existingPayment = paymentRepository.findByOrderId(orderId)
                .orElse(null);

        if (existingPayment != null) {
            return existingPayment;
        }

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setUserId(userId);
        payment.setAmount(amount);

        if (simulateFailure) {
            payment.setStatus(PaymentStatus.FAILED.name());
        } else {
            payment.setStatus(PaymentStatus.AUTHORIZED.name());
        }

        return paymentRepository.save(payment);
    }
    @Transactional(readOnly = true)
    public Payment getPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found for order: " + orderId));
    }

    @Transactional
    public Payment refundPayment(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment not found for order: " + orderId));

        if (PaymentStatus.AUTHORIZED.name().equals(payment.getStatus())) {
            payment.setStatus(PaymentStatus.REFUNDED.name());
            return paymentRepository.save(payment);
        }

        return payment;
    }
}