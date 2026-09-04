package com.ecommerce.management.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.dto.product.ProductRequest;
import com.ecommerce.management.dto.product.ProductResponse;
import com.ecommerce.management.entity.Category;
import com.ecommerce.management.entity.Product;
import com.ecommerce.management.entity.enums.RecordStatus;
import com.ecommerce.management.repository.CategoryRepository;
import com.ecommerce.management.repository.ProductRepository;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return productRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return toResponse(getProduct(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Category category = getCategory(request.categoryId());
        String sku = normalizeSku(request.sku());
        ensureSkuIsAvailable(sku, null);
        validatePriceAndStock(request.price(), request.stock());

        LocalDateTime now = LocalDateTime.now();
        Product product = new Product();
        product.setCategory(category);
        product.setName(request.name().trim());
        product.setSku(sku);
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setStatus(RecordStatus.ACTIVE);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getProduct(id);
        Category category = getCategory(request.categoryId());
        String sku = normalizeSku(request.sku());
        ensureSkuIsAvailable(sku, id);
        validatePriceAndStock(request.price(), request.stock());

        product.setCategory(category);
        product.setName(request.name().trim());
        product.setSku(sku);
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setUpdatedAt(LocalDateTime.now());

        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateStatus(Long id, RecordStatus status) {
        Product product = getProduct(id);
        product.setStatus(status);
        product.setUpdatedAt(LocalDateTime.now());

        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateStock(Long id, Integer stock) {
        if (stock == null || stock < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Product stock cannot be negative");
        }

        Product product = getProduct(id);
        product.setStock(stock);
        product.setUpdatedAt(LocalDateTime.now());

        return toResponse(productRepository.save(product));
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Product not found: " + id));
    }

    private Category getCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found: " + id));
    }

    private void ensureSkuIsAvailable(String sku, Long currentProductId) {
        boolean exists = currentProductId == null
                ? productRepository.existsBySku(sku)
                : productRepository.existsBySkuAndIdNot(sku, currentProductId);

        if (exists) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Product SKU already exists: " + sku);
        }
    }

    private void validatePriceAndStock(BigDecimal price, Integer stock) {
        if (price == null || price.signum() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Product price cannot be negative");
        }

        if (stock == null || stock < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Product stock cannot be negative");
        }
    }

    private String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getName(),
                product.getSku(),
                product.getPrice(),
                product.getStock(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
