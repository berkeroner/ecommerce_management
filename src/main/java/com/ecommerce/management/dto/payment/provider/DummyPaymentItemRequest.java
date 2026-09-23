package com.ecommerce.management.dto.payment.provider;

import java.math.BigDecimal;

public record DummyPaymentItemRequest(
        String productName,
        Integer quantity,
        BigDecimal unitPrice
) {
}
