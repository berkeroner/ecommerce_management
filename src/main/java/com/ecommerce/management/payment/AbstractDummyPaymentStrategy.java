package com.ecommerce.management.payment;

import java.util.UUID;

import com.ecommerce.management.client.DummyPaymentClient;
import com.ecommerce.management.dto.payment.provider.DummyPaymentAcceptedResponse;
import com.ecommerce.management.dto.payment.provider.DummyPaymentRequest;
import com.ecommerce.management.dto.payment.provider.DummyRefundResponse;

abstract class AbstractDummyPaymentStrategy implements PaymentStrategy {

    private final DummyPaymentClient client;

    protected AbstractDummyPaymentStrategy(DummyPaymentClient client) {
        this.client = client;
    }

    @Override
    public String provider() {
        return "dummy-payment-service";
    }

    @Override
    public DummyPaymentAcceptedResponse start(
            UUID idempotencyKey, DummyPaymentRequest request) {
        return client.createPayment(idempotencyKey, request);
    }

    @Override
    public DummyRefundResponse refund(UUID providerPaymentId) {
        return client.refundPayment(providerPaymentId, method());
    }

    protected boolean hasToken(String token) {
        return token != null && !token.isBlank();
    }
}
