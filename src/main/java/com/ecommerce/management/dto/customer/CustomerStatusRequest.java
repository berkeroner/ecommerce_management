package com.ecommerce.management.dto.customer;

import com.ecommerce.management.entity.enums.RecordStatus;

import jakarta.validation.constraints.NotNull;

public record CustomerStatusRequest(
        @NotNull RecordStatus status) {
}
