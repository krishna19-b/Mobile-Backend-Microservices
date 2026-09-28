package com.krishna.productservice.repository;

import com.krishna.productservice.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}