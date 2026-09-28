package com.krishna.orderservice.service;

import com.krishna.orderservice.client.ProductClient;
import com.krishna.orderservice.dto.request.OrderItemRequest;
import com.krishna.orderservice.dto.request.OrderRequest;
import com.krishna.orderservice.dto.response.OrderItemResponse;
import com.krishna.orderservice.dto.response.OrderResponse;
import com.krishna.orderservice.dto.response.ProductResponse;
import com.krishna.orderservice.entity.Order;
import com.krishna.orderservice.entity.OrderItem;
import com.krishna.orderservice.entity.OrderStatus;
import com.krishna.orderservice.repository.OrderItemRepository;
import com.krishna.orderservice.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductClient productClient;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productClient = productClient;
    }

    public OrderResponse createOrder(OrderRequest request) {

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {

            ProductResponse product = productClient.getProduct(itemRequest.getProductId());

            if (itemRequest.getQuantity() == null ||
                    itemRequest.getQuantity() <= 0) {

                throw new RuntimeException(
                        "Quantity must be greater than zero"
                );
            }

            if (product.getStockQuantity() == null ||
                    product.getStockQuantity() <= 0) {

                throw new RuntimeException(
                        "Product is out of stock: " +
                                itemRequest.getProductId()
                );
            }

            if (itemRequest.getQuantity() > product.getStockQuantity()) {

                throw new RuntimeException(
                        "Insufficient stock for product: " +
                                itemRequest.getProductId()
                );
            }

            BigDecimal subtotal =
                    product.getPrice().multiply(
                            BigDecimal.valueOf(itemRequest.getQuantity())
                    );

            totalAmount = totalAmount.add(subtotal);
        }

        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setStatus(OrderStatus.PLACED);
        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);

        for (OrderItemRequest itemRequest : request.getItems()) {

            ProductResponse product =
                    productClient.getProduct(itemRequest.getProductId());

            productClient.reduceStock(
                    product.getId(),
                    itemRequest.getQuantity()
            );

            BigDecimal price = product.getPrice();

            BigDecimal subtotal =
                    price.multiply(
                            BigDecimal.valueOf(itemRequest.getQuantity())
                    );

            OrderItem orderItem = new OrderItem();

            orderItem.setOrderId(savedOrder.getId());
            orderItem.setProductId(product.getId());
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setPrice(price);
            orderItem.setSubtotal(subtotal);

            orderItemRepository.save(orderItem);
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

    public OrderResponse updateOrderStatus(Long id, OrderStatus status) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Order not found"));

        order.setStatus(status);

        Order updatedOrder =
                orderRepository.save(order);

        return mapToResponse(updatedOrder);
    }

    public void deleteOrder(Long id) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Order not found"));

        List<OrderItem> items =
                orderItemRepository.findByOrderId(id);

        orderItemRepository.deleteAll(items);
        orderRepository.delete(order);
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