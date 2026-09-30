package com.krishna.orderservice.service;

import com.krishna.orderservice.client.ProductClient;
import com.krishna.orderservice.dto.response.ProductResponse;
import com.krishna.orderservice.exception.ProductServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;

@Service
public class ProductResilienceService {
    private final ProductClient productClient;
    public ProductResilienceService(ProductClient  productClient){
        this.productClient = productClient;
    }
    @Retry(name = "productService")
    @CircuitBreaker(name = "productService", fallbackMethod = "productServiceFallback")
    public ProductResponse getProductFromProductService(Long productId) {
        return productClient.getProduct(productId);
    }

    private ProductResponse productServiceFallback(Long productId, Throwable throwable) {

        throw new ProductServiceUnavailableException("Product service is temporarily unavailable");
    }
}
