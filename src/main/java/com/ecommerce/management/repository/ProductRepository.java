package com.ecommerce.management.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecommerce.management.entity.Product;
import com.ecommerce.management.entity.enums.RecordStatus;

import org.springframework.data.jpa.repository.Modifying;
import java.time.LocalDateTime;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findAllByCategoryId(Long categoryId, Pageable pageable);

    Page<Product> findAllByStatus(RecordStatus status, Pageable pageable);

    Page<Product> findAllByCategoryIdAndStatus(Long categoryId, RecordStatus status, Pageable pageable);

    @Query("""
            select p from Product p
            where (:categoryId is null or p.category.id = :categoryId)
              and (:status is null or p.status = :status)
              and (locate(:term, lower(p.name)) > 0 or locate(:term, lower(p.sku)) > 0)
            """)
    Page<Product> search(@Param("categoryId") Long categoryId, @Param("status") RecordStatus status, @Param("term") String term, Pageable pageable);

    @Modifying
    @Query("""
            update Product p
            set p.stock = p.stock - :quantity,
                p.updatedAt = :now
            where p.id = :id
              and p.status = :status
              and p.stock >= :quantity
            """)
            
    int reserveStock(
            @Param("id") Long id,
            @Param("quantity") int quantity,
            @Param("status") RecordStatus status,
            @Param("now") LocalDateTime now
    );

    boolean existsByCategoryId(Long categoryId);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);
}
