package com.ecommerce.management.dto.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ecommerce.management.entity.enums.RecordStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ProductResponse(
        Long id,
        @JsonProperty("category_id")
        Long categoryId,
        @JsonProperty("category_name")
        String categoryName,
        String name,
        String sku,
        BigDecimal price,
        Integer stock,
        RecordStatus status,
        @JsonProperty("created_at")
        LocalDateTime createdAt,
        @JsonProperty("updated_at")
        LocalDateTime updatedAt
) {
}
