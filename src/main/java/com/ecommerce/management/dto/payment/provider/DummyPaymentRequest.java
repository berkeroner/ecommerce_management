package com.ecommerce.management.dto.payment.provider;

import java.util.List;

import com.ecommerce.management.entity.enums.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonProperty;

public record DummyPaymentRequest(
        String orderId,
        PaymentMethod method,
        @JsonProperty("paymentToken") String paymentToken,
        List<DummyPaymentItemRequest> items,
        String currency
) {
}
