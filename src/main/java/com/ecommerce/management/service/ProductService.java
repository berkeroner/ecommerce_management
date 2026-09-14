package com.ecommerce.management.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.ecommerce.management.dto.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    public PageResponse<ProductResponse> findAll(int page, int limit, Long categoryId, String status, String search, String sort) {
        if (page < 1 || limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.valueOf(422),
                    "page must be at least 1 and limit must be between 1 and 100");
        }
        if (categoryId != null && categoryId < 1) {
            throw new ResponseStatusException(HttpStatus.valueOf(422), "category_id must be positive");
        }
        RecordStatus recordStatus = null;
        if (status != null) {
            recordStatus = switch (status) {
                case "active" -> RecordStatus.ACTIVE;
                case "passive" -> RecordStatus.PASSIVE;
                default -> throw new ResponseStatusException(HttpStatus.valueOf(422),
                        "status must be active or passive");
            };
        }
        var pageable = PageRequest.of(page - 1, limit, parseSort(sort));
        org.springframework.data.domain.Page<Product> products;
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        if (!term.isEmpty()) {
            products = productRepository.search(categoryId, recordStatus, term, pageable);
        } else if (categoryId != null && recordStatus != null) {
            products = productRepository.findAllByCategoryIdAndStatus(categoryId, recordStatus, pageable);
        } else if (categoryId != null) {
            products = productRepository.findAllByCategoryId(categoryId, pageable);
        } else if (recordStatus != null) {
            products = productRepository.findAllByStatus(recordStatus, pageable);
        } else {
            products = productRepository.findAll(pageable);
        }
        return new PageResponse<>(products.getContent().stream().map(this::toResponse).toList(),
                page, limit, products.getTotalElements(), products.getTotalPages());
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

    private Sort parseSort(String sort) {
        if (sort == null) {
            return Sort.by("id");
        }
        String[] parts = sort.split(",", -1);
        if (parts.length != 2) {
            throw new ResponseStatusException(HttpStatus.valueOf(422),
                    "sort must use field,direction format, for example price,asc");
        }
        String property = switch (parts[0]) {
            case "price" -> "price";
            case "name" -> "name";
            case "created_at" -> "createdAt";
            default -> throw new ResponseStatusException(HttpStatus.valueOf(422),
                    "sort field must be price, name or created_at");
        };
        Sort.Direction direction = switch (parts[1]) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new ResponseStatusException(HttpStatus.valueOf(422),
                    "sort direction must be asc or desc");
        };
        return Sort.by(direction, property).and(Sort.by("id"));
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
