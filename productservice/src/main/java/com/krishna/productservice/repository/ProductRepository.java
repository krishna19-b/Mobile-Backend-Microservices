package com.krishna.productservice.repository;

import com.krishna.productservice.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategoryId(Long categoryId);

    List<Product> findByNameContainingIgnoreCase(String name);

    boolean existsByName(String name);

    @Modifying
    @Query("""
            update Product p
            set p.stockQuantity = p.stockQuantity - :quantity
            where p.id = :productId
            and p.stockQuantity >= :quantity
            """)
    int reduceStock(
            @Param("productId") Long productId,
            @Param("quantity") Integer quantity
    );

    @Modifying
    @Query("""
            update Product p
            set p.stockQuantity = p.stockQuantity + :quantity
            where p.id = :productId
            """)
    int releaseStock(
            @Param("productId") Long productId,
            @Param("quantity") Integer quantity
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :productId")
    java.util.Optional<Product> findByIdForUpdate(
            @Param("productId") Long productId
    );
}