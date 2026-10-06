package com.ecommerce.management.dto.payment.provider;

import java.util.UUID;

public record DummyPaymentAcceptedResponse(
        UUID paymentId,
        String orderId,
        UUID idempotencyKey,
        ProviderPaymentStatus status
) {
}
