
package com.krishna.orderservice.client;

import com.krishna.orderservice.dto.request.InventoryOperationRequest;
import com.krishna.orderservice.dto.response.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "productservice")
public interface ProductClient {

    @GetMapping("/api/v1/products/{id}")
    ProductResponse getProduct(@PathVariable("id") Long id);

    @PutMapping("/api/v1/products/{id}/stock")
    void reduceStock(
            @PathVariable("id") Long id,
            @RequestParam("quantity") Integer quantity);

    @PutMapping("/api/v1/products/{id}/stock/release")
    void releaseStock(
            @PathVariable("id") Long id,
            @RequestParam("quantity") Integer quantity);

    @PutMapping("/api/v1/products/{id}/stock/reserve")
    void reserveStock(
            @PathVariable("id") Long id,
            @RequestBody InventoryOperationRequest request);

    @PutMapping("/api/v1/products/{id}/stock/compensate")
    void compensateStock(
            @PathVariable("id") Long id,
            @RequestBody InventoryOperationRequest request);
}
