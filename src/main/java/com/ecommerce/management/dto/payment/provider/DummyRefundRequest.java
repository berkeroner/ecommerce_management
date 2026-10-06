package com.ecommerce.management.dto.payment.provider;

import com.ecommerce.management.entity.enums.PaymentMethod;

public record DummyRefundRequest(PaymentMethod method) {
}
