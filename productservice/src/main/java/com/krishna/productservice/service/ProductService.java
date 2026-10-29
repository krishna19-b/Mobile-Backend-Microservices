
package com.krishna.productservice.service;

import com.krishna.productservice.dto.request.InventoryOperationRequest;
import com.krishna.productservice.dto.request.ProductRequest;
import com.krishna.productservice.dto.response.ProductResponse;
import com.krishna.productservice.entity.Category;
import com.krishna.productservice.entity.InventoryOperation;
import com.krishna.productservice.entity.Product;
import com.krishna.productservice.exception.InsufficientStockException;
import com.krishna.productservice.repository.CategoryRepository;
import com.krishna.productservice.repository.InventoryOperationRepository;
import com.krishna.productservice.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryOperationRepository inventoryOperationRepository;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            InventoryOperationRepository inventoryOperationRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryOperationRepository = inventoryOperationRepository;
    }

    public ProductResponse createProduct(ProductRequest request) {

        if (productRepository.existsByName(request.getName())) {
            throw new RuntimeException("Product already exists");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        Product product = new Product();
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setDescription(request.getDescription());
        product.setStockQuantity(request.getStockQuantity());
        product.setCategory(category);

        Product savedProduct = productRepository.save(product);

        return mapToResponse(savedProduct);
    }

    public ProductResponse getProductById(Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        return mapToResponse(product);
    }

    public List<ProductResponse> getAllProducts() {

        return productRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<ProductResponse> getProductsByCategory(Long categoryId) {

        return productRepository.findByCategoryId(categoryId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<ProductResponse> searchProducts(String name) {

        return productRepository.findByNameContainingIgnoreCase(name)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public ProductResponse updateProduct(Long id, ProductRequest request) {

        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        existingProduct.setName(request.getName());
        existingProduct.setPrice(request.getPrice());
        existingProduct.setDescription(request.getDescription());
        existingProduct.setStockQuantity(request.getStockQuantity());
        existingProduct.setCategory(category);

        Product updatedProduct = productRepository.save(existingProduct);

        return mapToResponse(updatedProduct);
    }

    public void deleteProduct(Long id) {

        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        productRepository.delete(existingProduct);
    }

    @Transactional
    public void reduceStock(Long productId, Integer quantity) {

        validateQuantity(quantity);

        int updatedRows = productRepository.reduceStock(
                productId,
                quantity
        );

        if (updatedRows == 0) {
            throw new InsufficientStockException(
                    "Insufficient stock or product not found: " + productId
            );
        }
    }

    @Transactional
    public void releaseStock(Long productId, Integer quantity) {

        validateQuantity(quantity);

        int updatedRows = productRepository.releaseStock(
                productId,
                quantity
        );

        if (updatedRows == 0) {
            throw new RuntimeException(
                    "Product not found while releasing stock: " + productId
            );
        }
    }

    @Transactional
    public void reserveStock(
            Long productId,
            InventoryOperationRequest request) {

        validateQuantity(request.getQuantity());

        Product product = productRepository.findByIdForUpdate(productId).orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        InventoryOperation existingOperation = inventoryOperationRepository.findByOperationKey(request.getOperationKey()).orElse(null);

        if (existingOperation != null) {
            validateExistingOperation(
                    existingOperation,
                    productId,
                    request.getQuantity(),
                    InventoryOperation.OperationType.RESERVATION
            );
            return;
        }

        if (product.getStockQuantity() < request.getQuantity()) {
            throw new InsufficientStockException(
                    "Insufficient stock for product: " + productId
            );
        }

        product.setStockQuantity(
                product.getStockQuantity() - request.getQuantity()
        );

        InventoryOperation operation = new InventoryOperation();
        operation.setOperationKey(request.getOperationKey());
        operation.setProductId(productId);
        operation.setQuantity(request.getQuantity());
        operation.setType(InventoryOperation.OperationType.RESERVATION);

        productRepository.save(product);
        inventoryOperationRepository.save(operation);
    }

    @Transactional
    public void compensateStock(
            Long productId,
            InventoryOperationRequest request) {

        validateQuantity(request.getQuantity());

        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() ->
                        new RuntimeException("Product not found: " + productId));

        InventoryOperation existingOperation =
                inventoryOperationRepository
                        .findByOperationKey(request.getOperationKey())
                        .orElse(null);

        if (existingOperation != null) {
            if (existingOperation.getType()
                    == InventoryOperation.OperationType.COMPENSATION) {
                validateExistingOperation(
                        existingOperation,
                        productId,
                        request.getQuantity(),
                        InventoryOperation.OperationType.COMPENSATION
                );
                return;
            }

            validateExistingOperation(
                    existingOperation,
                    productId,
                    request.getQuantity(),
                    InventoryOperation.OperationType.RESERVATION
            );

            String compensationKey = request.getOperationKey() + ":COMPENSATION";

            InventoryOperation existingCompensation =
                    inventoryOperationRepository
                            .findByOperationKey(compensationKey)
                            .orElse(null);

            if (existingCompensation != null) {
                validateExistingOperation(
                        existingCompensation,
                        productId,
                        request.getQuantity(),
                        InventoryOperation.OperationType.COMPENSATION
                );
                return;
            }

            product.setStockQuantity(
                    product.getStockQuantity() + request.getQuantity()
            );

            InventoryOperation compensation = new InventoryOperation();
            compensation.setOperationKey(compensationKey);
            compensation.setProductId(productId);
            compensation.setQuantity(request.getQuantity());
            compensation.setType(
                    InventoryOperation.OperationType.COMPENSATION
            );

            productRepository.save(product);
            inventoryOperationRepository.save(compensation);
            return;
        }

        throw new IllegalStateException(
                "Cannot compensate stock because reservation was not found: "
                        + request.getOperationKey()
        );
    }

    private void validateExistingOperation(
            InventoryOperation operation,
            Long productId,
            Integer quantity,
            InventoryOperation.OperationType expectedType) {

        if (!operation.getProductId().equals(productId) || !operation.getQuantity().equals(quantity) || operation.getType() != expectedType) {
            throw new IllegalStateException("Inventory operation key was reused with different details: " + operation.getOperationKey());
        }
    }

    private void validateQuantity(Integer quantity) {

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero"
            );
        }
    }

    private ProductResponse mapToResponse(Product product) {

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getDescription(),
                product.getStockQuantity(),
                product.getCategory().getId(),
                product.getCategory().getName()
        );
    }
}
