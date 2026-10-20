package com.ecommerce.management.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ecommerce.management.entity.enums.OrderStatus;
import com.ecommerce.management.entity.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CustomerOrderResponse(
        Long id,
        @JsonProperty("order_no") String orderNo,
        OrderStatus status,
        @JsonProperty("payment_status") PaymentStatus paymentStatus,
        @JsonProperty("total_amount") BigDecimal totalAmount,
        String currency,
        @JsonProperty("created_at") LocalDateTime createdAt) {
}
