package com.ecommerce.management.entity.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum OrderStatus {
    @JsonProperty("pending") PENDING,
    @JsonProperty("processing") PROCESSING,
    @JsonProperty("confirmed") CONFIRMED,
    @JsonProperty("failed") FAILED,
    @JsonProperty("cancelled") CANCELLED
}
