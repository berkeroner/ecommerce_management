package com.ecommerce.management.dto.address;

import java.time.LocalDateTime;
import com.ecommerce.management.entity.enums.AddressType;

public record AddressResponse(
        Long id,
        Long customerId,
        AddressType addressType,
        String title,
        String city,
        String district,
        String addressLine,
        String postalCode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
