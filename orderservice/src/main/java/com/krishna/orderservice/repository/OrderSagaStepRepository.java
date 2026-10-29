
package com.krishna.orderservice.repository;

import com.krishna.orderservice.entity.OrderSagaStep;
import com.krishna.orderservice.entity.SagaStepStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderSagaStepRepository
        extends JpaRepository<OrderSagaStep, Long> {

    List<OrderSagaStep> findByOrderIdOrderByOrderItemIdAsc(Long orderId);

    Optional<OrderSagaStep> findByOrderItemId(Long orderItemId);

    List<OrderSagaStep> findByOrderIdAndStatus(
            Long orderId,
            SagaStepStatus status
    );
}
