package com.ecommerce.management.payment;

import com.ecommerce.management.entity.enums.PaymentStatus;

public record PaymentResult(
        PaymentStatus status,
        String transactionId,
        String failureReason
) {
    public static PaymentResult completed(String transactionId) {
        return new PaymentResult(PaymentStatus.COMPLETED, transactionId, null);
    }

    public static PaymentResult pending(String transactionId) {
        return new PaymentResult(PaymentStatus.PENDING, transactionId, null);
    }

    public static PaymentResult failed(String reason) {
        return new PaymentResult(PaymentStatus.FAILED, null, reason);
    }
}
