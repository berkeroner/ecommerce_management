package com.ecommerce.management.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommerce.management.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByCategoryId(Long categoryId);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);
}
