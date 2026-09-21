package com.ecommerce.management.dto.payment;

import com.ecommerce.management.entity.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
        @NotNull PaymentMethod method,
        String paymentToken
) {
}
