package com.ecommerce.management.dto.product;

import com.ecommerce.management.entity.enums.RecordStatus;

import jakarta.validation.constraints.NotNull;

public record ProductStatusRequest(
        @NotNull RecordStatus status) {
}
