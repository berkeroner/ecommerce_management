package com.ecommerce.management.dto.customer;

import java.time.LocalDateTime;

import com.ecommerce.management.entity.enums.RecordStatus;

public record CustomerResponse(
        Long id,
        String name,
        String email,
        RecordStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}