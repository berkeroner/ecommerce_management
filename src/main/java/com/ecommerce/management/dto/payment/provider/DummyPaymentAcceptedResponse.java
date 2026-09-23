package com.ecommerce.management.dto.payment.provider;

import java.util.UUID;

public record DummyPaymentAcceptedResponse(
        UUID paymentId,
        String orderId,
        ProviderPaymentStatus status
) {
}
