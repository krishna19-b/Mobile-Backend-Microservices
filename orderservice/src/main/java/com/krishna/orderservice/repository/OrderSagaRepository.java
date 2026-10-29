
package com.krishna.orderservice.repository;

import com.krishna.orderservice.entity.OrderSaga;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderSagaRepository extends JpaRepository<OrderSaga, Long> {

    Optional<OrderSaga> findByOrderId(Long orderId);
}
