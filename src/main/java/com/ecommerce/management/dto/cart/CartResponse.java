package com.ecommerce.management.dto.cart;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CartResponse(
        Long id,
        List<CartItemResponse> items,
        int totalItems,
        BigDecimal subtotal,
        String currency,
        LocalDateTime updatedAt) {
}
