
package com.krishna.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krishna.orderservice.client.PaymentClient;
import com.krishna.orderservice.client.ProductClient;
import com.krishna.orderservice.dto.request.InventoryOperationRequest;
import com.krishna.orderservice.dto.request.PaymentAuthorizationRequest;
import com.krishna.orderservice.entity.Order;
import com.krishna.orderservice.entity.OrderItem;
import com.krishna.orderservice.entity.OrderSaga;
import com.krishna.orderservice.entity.OrderSagaStep;
import com.krishna.orderservice.entity.OrderStatus;
import com.krishna.orderservice.entity.SagaStatus;
import com.krishna.orderservice.entity.SagaStepStatus;
import com.krishna.orderservice.event.OrderCreatedEvent;
import com.krishna.orderservice.repository.OrderItemRepository;
import com.krishna.orderservice.repository.OrderRepository;
import com.krishna.orderservice.repository.OrderSagaRepository;
import com.krishna.orderservice.repository.OrderSagaStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class OrderSagaListener {

    private static final Logger log =
            LoggerFactory.getLogger(OrderSagaListener.class);

    private final ObjectMapper objectMapper;
    private final ProductClient productClient;
    private final PaymentClient paymentClient;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderSagaRepository orderSagaRepository;
    private final OrderSagaStepRepository orderSagaStepRepository;

    @Value("${saga.payment.simulate-failure:false}")
    private boolean simulatePaymentFailure;

    public OrderSagaListener(
            ObjectMapper objectMapper,
            ProductClient productClient,
            PaymentClient paymentClient,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderSagaRepository orderSagaRepository,
            OrderSagaStepRepository orderSagaStepRepository) {

        this.objectMapper = objectMapper;
        this.productClient = productClient;
        this.paymentClient = paymentClient;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderSagaRepository = orderSagaRepository;
        this.orderSagaStepRepository = orderSagaStepRepository;
    }

    @KafkaListener(
            topics = "order.created",
            groupId = "order-saga-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCreated(String message) {

        log.info("Received order.created event: {}", message);

        try {
            OrderCreatedEvent event = objectMapper.readValue(
                    message,
                    OrderCreatedEvent.class
            );

            processSaga(event.getOrderId());

        } catch (Exception e) {
            log.error("Unable to process order.created event", e);
            throw new IllegalStateException(
                    "Failed to process order.created event", e
            );
        }
    }

    @Scheduled(
            fixedDelayString = "${saga.recovery.fixed-delay-ms:10000}"
    )
    public void recoverUnfinishedSagas() {

        List<OrderSaga> sagas = orderSagaRepository.findAll();

        for (OrderSaga saga : sagas) {

            if (saga.getStatus() == SagaStatus.COMPLETED
                    || saga.getStatus() == SagaStatus.FAILED) {
                continue;
            }

            try {
                log.info(
                        "Recovering saga for order {} with status {}",
                        saga.getOrderId(),
                        saga.getStatus()
                );

                processSaga(saga.getOrderId());

            } catch (Exception e) {
                log.error(
                        "Recovery failed for order {}",
                        saga.getOrderId(),
                        e
                );
            }
        }
    }

    private synchronized void processSaga(Long orderId) {

        Order order = orderRepository.findById(orderId).orElse(null);

        if (order == null) {
            log.error(
                    "Cannot process saga because order {} was not found",
                    orderId
            );
            return;
        }

        OrderSaga saga = orderSagaRepository.findByOrderId(orderId)
                .orElseGet(() -> {
                    OrderSaga newSaga = new OrderSaga();
                    newSaga.setOrderId(orderId);
                    newSaga.setStatus(SagaStatus.STARTED);
                    newSaga.setCurrentStep("INVENTORY_RESERVATION");
                    return orderSagaRepository.save(newSaga);
                });

        if (saga.getStatus() == SagaStatus.COMPLETED
                || saga.getStatus() == SagaStatus.FAILED) {
            return;
        }

        if (saga.getStatus() == SagaStatus.COMPENSATING
                || saga.getStatus() == SagaStatus.COMPENSATION_FAILED) {
            compensateInventory(order, saga);
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            log.info(
                    "Skipping order {} because its status is {}",
                    orderId,
                    order.getStatus()
            );
            return;
        }

        try {
            reserveInventory(orderId, saga);

            saga.setStatus(SagaStatus.INVENTORY_RESERVED);
            saga.setCurrentStep("PAYMENT_AUTHORIZATION");
            orderSagaRepository.save(saga);

            saga.setStatus(SagaStatus.PAYMENT_PENDING);
            orderSagaRepository.save(saga);

        } catch (Exception inventoryError) {

            log.error(
                    "Inventory reservation failed for order {}",
                    orderId,
                    inventoryError
            );

            saga.setCurrentStep("INVENTORY_RESERVATION_RETRY_REQUIRED");
            orderSagaRepository.save(saga);

            // Keep the saga recoverable and retry using the same operation keys.
            // A timed-out remote reservation may already have succeeded.
            return;
        }

        authorizePayment(order, saga);
    }

    private void reserveInventory(Long orderId, OrderSaga saga) {

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);

        for (OrderItem item : items) {

            String operationKey = "order-item-" + item.getId();

            OrderSagaStep step = orderSagaStepRepository
                    .findByOrderItemId(item.getId())
                    .orElseGet(() -> {
                        OrderSagaStep newStep = new OrderSagaStep();
                        newStep.setOrderId(orderId);
                        newStep.setOrderItemId(item.getId());
                        newStep.setProductId(item.getProductId());
                        newStep.setQuantity(item.getQuantity());
                        newStep.setOperationKey(operationKey);
                        newStep.setStatus(SagaStepStatus.PENDING);
                        return orderSagaStepRepository.save(newStep);
                    });

            if (step.getStatus() == SagaStepStatus.RESERVED) {
                continue;
            }

            if (step.getStatus() == SagaStepStatus.COMPENSATED
                    || step.getStatus() == SagaStepStatus.COMPENSATION_FAILED) {
                throw new IllegalStateException(
                        "Cannot reserve inventory for order item "
                                + item.getId()
                                + " because its step status is "
                                + step.getStatus()
                );
            }

            // Reuse the same operation key to avoid duplicate reservations.
            productClient.reserveStock(
                    item.getProductId(),
                    new InventoryOperationRequest(
                            step.getOperationKey(),
                            step.getQuantity()
                    )
            );

            step.setStatus(SagaStepStatus.RESERVED);
            orderSagaStepRepository.save(step);

            log.info(
                    "Inventory reserved for order {}, item {}, product {}",
                    orderId,
                    item.getId(),
                    item.getProductId()
            );
        }

        saga.setStatus(SagaStatus.INVENTORY_RESERVED);
        saga.setCurrentStep("INVENTORY_RESERVED");
        orderSagaRepository.save(saga);
    }

    private void authorizePayment(Order order, OrderSaga saga) {

        Long orderId = order.getId();

        try {
            Map<String, Object> paymentResponse =
                    paymentClient.authorizePayment(
                            new PaymentAuthorizationRequest(
                                    orderId,
                                    order.getUserId(),
                                    order.getTotalAmount(),
                                    simulatePaymentFailure
                            )
                    );

            String paymentStatus =
                    String.valueOf(paymentResponse.get("status"));

            if ("AUTHORIZED".equalsIgnoreCase(paymentStatus)) {
                completeSaga(order, saga);
                return;
            }

            if ("FAILED".equalsIgnoreCase(paymentStatus)
                    || "REFUNDED".equalsIgnoreCase(paymentStatus)) {
                compensateInventory(order, saga);
                return;
            }

            deferPaymentCheck(saga, orderId, paymentStatus);

        } catch (Exception paymentError) {

            log.error(
                    "Payment authorization response is uncertain for order {}",
                    orderId,
                    paymentError
            );

            try {
                Map<String, Object> paymentResponse =
                        paymentClient.getPaymentByOrderId(orderId);

                String paymentStatus =
                        String.valueOf(paymentResponse.get("status"));

                if ("AUTHORIZED".equalsIgnoreCase(paymentStatus)) {
                    completeSaga(order, saga);
                    return;
                }

                if ("FAILED".equalsIgnoreCase(paymentStatus)
                        || "REFUNDED".equalsIgnoreCase(paymentStatus)) {
                    compensateInventory(order, saga);
                    return;
                }

                deferPaymentCheck(saga, orderId, paymentStatus);

            } catch (Exception lookupError) {

                log.error(
                        "Unable to confirm payment status for order {}. "
                                + "Saga will be retried.",
                        orderId,
                        lookupError
                );

                deferPaymentCheck(
                        saga,
                        orderId,
                        "PAYMENT_STATUS_CHECK_REQUIRED"
                );
            }
        }
    }

    private void deferPaymentCheck(
            OrderSaga saga,
            Long orderId,
            String paymentStatus) {

        saga.setStatus(SagaStatus.PAYMENT_PENDING);
        saga.setCurrentStep("PAYMENT_STATUS_CHECK_REQUIRED");
        orderSagaRepository.save(saga);

        log.warn(
                "Deferring order {} because payment status is {}",
                orderId,
                paymentStatus
        );
    }

    private void completeSaga(Order order, OrderSaga saga) {

        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        saga.setStatus(SagaStatus.COMPLETED);
        saga.setCurrentStep("COMPLETED");
        saga.setCompletedAt(LocalDateTime.now());
        orderSagaRepository.save(saga);

        log.info(
                "Saga completed. Order {} is CONFIRMED",
                order.getId()
        );
    }

    private void compensateInventory(Order order, OrderSaga saga) {

        Long orderId = order.getId();

        saga.setStatus(SagaStatus.COMPENSATING);
        saga.setCurrentStep("INVENTORY_COMPENSATION");
        orderSagaRepository.save(saga);

        List<OrderSagaStep> steps =
                orderSagaStepRepository.findByOrderIdOrderByOrderItemIdAsc(
                        orderId
                );

        boolean compensationFailed = false;

        for (OrderSagaStep step : steps) {

            if (step.getStatus() == SagaStepStatus.COMPENSATED) {
                continue;
            }

            try {
                // Reconcile a possibly timed-out reservation before compensating.
                if (step.getStatus() == SagaStepStatus.PENDING) {

                    productClient.reserveStock(
                            step.getProductId(),
                            new InventoryOperationRequest(
                                    step.getOperationKey(),
                                    step.getQuantity()
                            )
                    );

                    step.setStatus(SagaStepStatus.RESERVED);
                    orderSagaStepRepository.save(step);
                }

                if (step.getStatus() == SagaStepStatus.RESERVED
                        || step.getStatus() == SagaStepStatus.COMPENSATION_FAILED) {

                    productClient.compensateStock(
                            step.getProductId(),
                            new InventoryOperationRequest(
                                    step.getOperationKey(),
                                    step.getQuantity()
                            )
                    );

                    step.setStatus(SagaStepStatus.COMPENSATED);
                    orderSagaStepRepository.save(step);
                }

            } catch (Exception compensationError) {

                compensationFailed = true;

                step.setStatus(SagaStepStatus.COMPENSATION_FAILED);
                orderSagaStepRepository.save(step);

                log.error(
                        "Compensation failed for order {}, item {}. "
                                + "It will be retried.",
                        orderId,
                        step.getOrderItemId(),
                        compensationError
                );
            }
        }

        if (compensationFailed) {

            saga.setStatus(SagaStatus.COMPENSATION_FAILED);
            saga.setCurrentStep(
                    "INVENTORY_COMPENSATION_RETRY_REQUIRED"
            );
            orderSagaRepository.save(saga);

            return;
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        saga.setStatus(SagaStatus.FAILED);
        saga.setCurrentStep(
                "COMPENSATION_COMPLETED_ORDER_CANCELLED"
        );
        saga.setCompletedAt(LocalDateTime.now());
        orderSagaRepository.save(saga);

        log.info(
                "Inventory compensation completed. Order {} is CANCELLED",
                orderId
        );
    }
}
