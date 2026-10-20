package com.ecommerce.management.dto.cart;

import java.math.BigDecimal;

public record CartItemResponse(
        Long productId,
        String name,
        String sku,
        Long categoryId,
        String categoryName,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineTotal,
        Integer availableStock) {
}
