package com.ecommerce.management.dto.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentCallbackRequest(
        @JsonProperty("paymentId") @NotNull UUID paymentId,
        @JsonProperty("orderId") @NotBlank String orderId,
        @NotNull ProviderPaymentStatus status,
        @JsonProperty("totalAmount") @NotNull @DecimalMin("0.01") BigDecimal totalAmount,
        @NotBlank String currency,
        String message
) {
}
