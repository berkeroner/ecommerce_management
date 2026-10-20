package com.ecommerce.management.dto.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateCartItemRequest(
        @NotNull @Positive @Max(100) Integer quantity) {
}
