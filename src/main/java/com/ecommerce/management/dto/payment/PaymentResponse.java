package com.ecommerce.management.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ecommerce.management.entity.enums.PaymentMethod;
import com.ecommerce.management.entity.enums.PaymentStatus;

public record PaymentResponse(
        Long id,
        String paymentNo,
        Long orderId,
        PaymentMethod method,
        String provider,
        PaymentStatus status,
        BigDecimal amount,
        String transactionId,
        String failureReason,
        LocalDateTime paidAt
) {
}
