package com.krishna.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krishna.orderservice.client.ProductClient;
import com.krishna.orderservice.dto.request.OrderItemRequest;
import com.krishna.orderservice.dto.request.OrderRequest;
import com.krishna.orderservice.dto.response.OrderItemResponse;
import com.krishna.orderservice.dto.response.OrderResponse;
import com.krishna.orderservice.dto.response.ProductResponse;
import com.krishna.orderservice.entity.Order;
import com.krishna.orderservice.entity.OrderItem;
import com.krishna.orderservice.entity.OrderStatus;
import com.krishna.orderservice.entity.OutboxEvent;
import com.krishna.orderservice.entity.OutboxEventStatus;
import com.krishna.orderservice.event.OrderCancelledEvent;
import com.krishna.orderservice.event.OrderCreatedEvent;
import com.krishna.orderservice.repository.OrderItemRepository;
import com.krishna.orderservice.repository.OrderRepository;
import com.krishna.orderservice.repository.OutboxEventRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProductClient productClient;
    private final ProductResilienceService productResilienceService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OutboxEventRepository outboxEventRepository,
            ProductClient productClient,
            ProductResilienceService productResilienceService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper) {

        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.productClient = productClient;
        this.productResilienceService = productResilienceService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {

            if (itemRequest.getQuantity() == null
                    || itemRequest.getQuantity() <= 0) {

                throw new RuntimeException(
                        "Quantity must be greater than zero");
            }

            ProductResponse product =
                    productResilienceService.getProductFromProductService(
                            itemRequest.getProductId());

            if (product.getStockQuantity() == null
                    || product.getStockQuantity() <= 0) {

                throw new RuntimeException(
                        "Product is out of stock: "
                                + itemRequest.getProductId());
            }

            if (itemRequest.getQuantity() > product.getStockQuantity()) {

                throw new RuntimeException(
                        "Insufficient stock for product: "
                                + itemRequest.getProductId());
            }

            BigDecimal subtotal = product.getPrice().multiply(
                    BigDecimal.valueOf(itemRequest.getQuantity()));

            totalAmount = totalAmount.add(subtotal);
        }

        Order order = new Order();

        order.setUserId(request.getUserId());
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);

        for (OrderItemRequest itemRequest : request.getItems()) {

            ProductResponse product =
                    productResilienceService.getProductFromProductService(
                            itemRequest.getProductId());

            BigDecimal price = product.getPrice();

            BigDecimal subtotal = price.multiply(
                    BigDecimal.valueOf(itemRequest.getQuantity()));

            OrderItem orderItem = new OrderItem();

            orderItem.setOrderId(savedOrder.getId());
            orderItem.setProductId(product.getId());
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setPrice(price);
            orderItem.setSubtotal(subtotal);

            orderItemRepository.save(orderItem);
        }

        String correlationId = MDC.get("X-Correlation-ID");

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        LocalDateTime occurredAt = LocalDateTime.now();

        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                "ORDER_CREATED",
                1,
                savedOrder.getId(),
                savedOrder.getUserId(),
                savedOrder.getTotalAmount(),
                occurredAt,
                correlationId
        );

        try {

            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = new OutboxEvent(
                    event.getEventId(),
                    event.getEventType(),
                    "order.created",
                    payload,
                    OutboxEventStatus.PENDING.name(),
                    occurredAt
            );

            outboxEventRepository.save(outboxEvent);

        } catch (JsonProcessingException e) {

            throw new RuntimeException(
                    "Failed to create outbox event payload", e);
        }

        return mapToResponse(savedOrder);
    }

    public OrderResponse getOrderById(Long id) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Order not found"));

        return mapToResponse(order);
    }

    public List<OrderResponse> getAllOrders() {

        return orderRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<OrderResponse> getOrdersByUserId(Long userId) {

        return orderRepository.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<OrderResponse> getOrdersByStatus(OrderStatus status) {

        return orderRepository.findByStatus(status)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public OrderResponse updateOrderStatus(
            Long id,
            OrderStatus status) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Order not found"));

        order.setStatus(status);

        Order updatedOrder = orderRepository.save(order);

        return mapToResponse(updatedOrder);
    }

    @Transactional
    public void deleteOrder(Long id) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Order not found"));

        List<OrderItem> items =
                orderItemRepository.findByOrderId(id);

        orderItemRepository.deleteAll(items);
        orderRepository.delete(order);

        String correlationId = MDC.get("X-Correlation-ID");

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        LocalDateTime occurredAt = LocalDateTime.now();

        OrderCancelledEvent event = new OrderCancelledEvent(
                UUID.randomUUID().toString(),
                "ORDER_CANCELLED",
                1,
                order.getId(),
                order.getUserId(),
                occurredAt,
                correlationId
        );

        try {

            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = new OutboxEvent(
                    event.getEventId(),
                    event.getEventType(),
                    "order.cancelled",
                    payload,
                    OutboxEventStatus.PENDING.name(),
                    occurredAt
            );

            outboxEventRepository.save(outboxEvent);

        } catch (JsonProcessingException e) {

            throw new RuntimeException(
                    "Failed to create order-cancelled outbox event payload", e);
        }
    }

    private OrderResponse mapToResponse(Order order) {

        List<OrderItemResponse> itemResponses =
                orderItemRepository.findByOrderId(order.getId())
                        .stream()
                        .map(this::mapItemToResponse)
                        .toList();

        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                itemResponses
        );
    }

    private OrderItemResponse mapItemToResponse(OrderItem item) {

        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getQuantity(),
                item.getPrice(),
                item.getSubtotal()
        );
    }
}