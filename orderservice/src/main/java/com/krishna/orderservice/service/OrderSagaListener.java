package com.krishna.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krishna.orderservice.client.PaymentClient;
import com.krishna.orderservice.client.ProductClient;
import com.krishna.orderservice.dto.request.PaymentAuthorizationRequest;
import com.krishna.orderservice.entity.Order;
import com.krishna.orderservice.entity.OrderItem;
import com.krishna.orderservice.entity.OrderStatus;
import com.krishna.orderservice.event.OrderCreatedEvent;
import com.krishna.orderservice.repository.OrderItemRepository;
import com.krishna.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OrderSagaListener {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaListener.class);

    private final ObjectMapper objectMapper;
    private final ProductClient productClient;
    private final PaymentClient paymentClient;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Value("${saga.payment.simulate-failure:false}")
    private boolean simulatePaymentFailure;

    public OrderSagaListener(
            ObjectMapper objectMapper,
            ProductClient productClient,
            PaymentClient paymentClient,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository) {

        this.objectMapper = objectMapper;
        this.productClient = productClient;
        this.paymentClient = paymentClient;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @KafkaListener(topics = "order.created", groupId = "order-saga-group", containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderCreated(String message) {

        log.info("Received order.created event: {}", message);

        OrderCreatedEvent event;

        try {
            event = objectMapper.readValue(
                    message,
                    OrderCreatedEvent.class
            );
        } catch (Exception e) {
            log.error("Unable to deserialize order.created event", e);
            return;
        }

        Long orderId = event.getOrderId();

        Order order = orderRepository.findById(orderId).orElse(null);

        if (order == null) {
            log.error("Order not found: {}", orderId);
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            log.info(
                    "Skipping Saga for order {} because status is {}",
                    orderId,
                    order.getStatus()
            );
            return;
        }

        List<OrderItem> items =
                orderItemRepository.findByOrderId(orderId);

        List<OrderItem> reservedItems = new ArrayList<>();

        try {
            for (OrderItem item : items) {

                productClient.reduceStock(
                        item.getProductId(),
                        item.getQuantity()
                );

                reservedItems.add(item);

                log.info(
                        "Inventory reserved for order {}, product {}, quantity {}",
                        orderId,
                        item.getProductId(),
                        item.getQuantity()
                );
            }

            log.info("Inventory reservation completed for order {}", orderId);

            Map<String, Object> paymentResponse =
                    paymentClient.authorizePayment(
                            new PaymentAuthorizationRequest(
                                    orderId,
                                    event.getUserId(),
                                    event.getTotalAmount(),
                                    simulatePaymentFailure
                            )
                    );

            String paymentStatus =
                    String.valueOf(paymentResponse.get("status"));

            log.info(
                    "Payment response for order {}: {}",
                    orderId,
                    paymentStatus
            );

            if (!"AUTHORIZED".equalsIgnoreCase(paymentStatus)) {
                throw new IllegalStateException(
                        "Payment was not authorized: " + paymentStatus
                );
            }

            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);

            log.info(
                    "Saga completed successfully. Order {} is CONFIRMED",
                    orderId
            );

        } catch (Exception e) {

            log.error(
                    "Saga failed for order {}. Starting inventory compensation.",
                    orderId,
                    e
            );

            for (OrderItem item : reservedItems) {
                try {
                    productClient.releaseStock(
                            item.getProductId(),
                            item.getQuantity()
                    );

                    log.info(
                            "Inventory released for order {}, product {}, quantity {}",
                            orderId,
                            item.getProductId(),
                            item.getQuantity()
                    );

                } catch (Exception compensationError) {
                    log.error(
                            "Inventory release failed for order {}, product {}",
                            orderId,
                            item.getProductId(),
                            compensationError
                    );
                }
            }

            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            log.info("Order {} marked CANCELLED", orderId);
        }
    }
}
