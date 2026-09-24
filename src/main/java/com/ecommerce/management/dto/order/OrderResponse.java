package com.ecommerce.management.dto.order;

import com.ecommerce.management.entity.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record OrderResponse(

    Long id,
    @JsonProperty("order_no")
    String orderNo,
    OrderStatus status,
    @JsonProperty("total_amount")
    BigDecimal totalAmount,
    String currency

) {
}
