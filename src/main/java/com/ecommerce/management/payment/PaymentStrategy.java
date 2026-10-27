package com.ecommerce.management.payment;

import java.util.UUID;

import com.ecommerce.management.dto.payment.PaymentRequest;
import com.ecommerce.management.dto.payment.provider.DummyPaymentAcceptedResponse;
import com.ecommerce.management.dto.payment.provider.DummyPaymentRequest;
import com.ecommerce.management.dto.payment.provider.DummyRefundResponse;
import com.ecommerce.management.entity.enums.PaymentMethod;

public interface PaymentStrategy {

    PaymentMethod method();

    String provider();

    void validate(PaymentRequest request);

    DummyPaymentAcceptedResponse start(UUID idempotencyKey, DummyPaymentRequest request);

    DummyRefundResponse refund(UUID providerPaymentId);
}
