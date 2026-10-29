
package com.krishna.orderservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class InventoryOperationRequest {

    @NotBlank
    private String operationKey;

    @NotNull
    @Positive
    private Integer quantity;

    public InventoryOperationRequest() {
    }

    public InventoryOperationRequest(String operationKey, Integer quantity) {
        this.operationKey = operationKey;
        this.quantity = quantity;
    }

    public String getOperationKey() {
        return operationKey;
    }

    public void setOperationKey(String operationKey) {
        this.operationKey = operationKey;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
