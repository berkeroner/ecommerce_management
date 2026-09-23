package com.ecommerce.management.dto.payment.provider;

import java.util.List;

public record DummyPaymentRequest(
        String orderId,
        List<DummyPaymentItemRequest> items,
        String currency
) {
}
