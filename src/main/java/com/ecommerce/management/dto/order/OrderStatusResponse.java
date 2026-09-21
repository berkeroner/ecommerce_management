package com.ecommerce.management.dto.order;

import com.ecommerce.management.entity.enums.OrderStatus;

public record OrderStatusResponse(

    Long id,
    OrderStatus status
) {

}
