package com.ecommerce.management.entity.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ShipmentStatus {
    @JsonProperty("pending") PENDING,
    @JsonProperty("preparing") PREPARING,
    @JsonProperty("shipped") SHIPPED,
    @JsonProperty("delivered") DELIVERED,
    @JsonProperty("failed") FAILED
}
