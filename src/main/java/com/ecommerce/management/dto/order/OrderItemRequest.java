package com.ecommerce.management.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(

    @NotNull
    @Positive
    @JsonProperty("product_id")
    Long productId,

    @NotNull
    @Min(1)
    Integer quantity
) {
}
