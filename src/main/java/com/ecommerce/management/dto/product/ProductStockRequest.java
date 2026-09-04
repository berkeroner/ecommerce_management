package com.ecommerce.management.dto.product;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductStockRequest(
        @NotNull @PositiveOrZero Integer stock) {
}
