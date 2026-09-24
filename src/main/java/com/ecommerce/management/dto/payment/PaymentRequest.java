package com.ecommerce.management.dto.payment;

import com.ecommerce.management.entity.enums.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
        @NotNull PaymentMethod method,
        @JsonProperty("payment_token")
        String paymentToken
) {
}
