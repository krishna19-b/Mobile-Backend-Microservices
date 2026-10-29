
package com.krishna.productservice.repository;

import com.krishna.productservice.entity.InventoryOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryOperationRepository
        extends JpaRepository<InventoryOperation, Long> {

    Optional<InventoryOperation> findByOperationKey(String operationKey);
}
