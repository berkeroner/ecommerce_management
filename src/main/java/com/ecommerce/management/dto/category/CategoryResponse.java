package com.ecommerce.management.dto.category;

import java.time.LocalDateTime;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
