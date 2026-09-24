package com.ecommerce.management.dto.category;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        @JsonProperty("is_active")
        Boolean isActive,
        @JsonProperty("created_at")
        LocalDateTime createdAt,
        @JsonProperty("updated_at")
        LocalDateTime updatedAt) {
}
