package com.ecommerce.management.dto.customer;

import java.time.LocalDateTime;

import com.ecommerce.management.entity.enums.RecordStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CustomerResponse(
        Long id,
        String name,
        String email,
        RecordStatus status,
        @JsonProperty("created_at")
        LocalDateTime createdAt,
        @JsonProperty("updated_at")
        LocalDateTime updatedAt
) {
}
