package com.ecommerce.management.dto.address;

import java.time.LocalDateTime;
import com.ecommerce.management.entity.enums.AddressType;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AddressResponse(
        Long id,
        @JsonProperty("customer_id")
        Long customerId,
        @JsonProperty("address_type")
        AddressType addressType,
        String title,
        String city,
        String district,
        @JsonProperty("address_line")
        String addressLine,
        @JsonProperty("postal_code")
        String postalCode,
        @JsonProperty("created_at")
        LocalDateTime createdAt,
        @JsonProperty("updated_at")
        LocalDateTime updatedAt
) {
}
