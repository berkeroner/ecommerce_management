package com.ecommerce.management.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ecommerce.management.entity.enums.PaymentMethod;
import com.ecommerce.management.entity.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentResponse(
        Long id,
        @JsonProperty("payment_no")
        String paymentNo,
        @JsonProperty("order_id")
        Long orderId,
        PaymentMethod method,
        String provider,
        PaymentStatus status,
        BigDecimal amount,
        @JsonProperty("transaction_id")
        String transactionId,
        @JsonProperty("failure_reason")
        String failureReason,
        @JsonProperty("paid_at")
        LocalDateTime paidAt
) {
}
