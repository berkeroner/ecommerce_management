package com.ecommerce.management.dto.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(

    @NotNull
    @Positive
    Long productId,

    @NotNull
    @Min(1)
    Integer quantity
) {
}
