package com.ecommerce.management.dto.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ecommerce.management.entity.enums.RecordStatus;

public record ProductResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        String sku,
        BigDecimal price,
        Integer stock,
        RecordStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
