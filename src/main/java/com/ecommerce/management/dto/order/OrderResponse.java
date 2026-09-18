package com.ecommerce.management.dto.order;

import com.ecommerce.management.entity.enums.OrderStatus;
import java.math.BigDecimal;

public record OrderResponse(

    Long id,
    String orderNo,
    OrderStatus status,
    BigDecimal totalAmount,
    String currency
    
) {
}
