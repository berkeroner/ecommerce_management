package com.ecommerce.management.dto.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProviderPaymentStatusResponse(
        @JsonProperty("paymentId") UUID paymentId,
        @JsonProperty("orderId") String orderId,
        @JsonProperty("idempotencyKey") UUID idempotencyKey,
        ProviderPaymentStatus status,
        @JsonProperty("totalAmount") BigDecimal totalAmount,
        String currency,
        String message
) {
    public PaymentCallbackRequest toCallbackRequest() {
        return new PaymentCallbackRequest(
                paymentId, orderId, idempotencyKey, status,
                totalAmount, currency, message);
    }
}
