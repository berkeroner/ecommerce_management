package com.ecommerce.management.entity.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PaymentStatus {
    @JsonProperty("pending") PENDING,
    @JsonProperty("processing") PROCESSING,
    @JsonProperty("completed") COMPLETED,
    @JsonProperty("failed") FAILED,
    @JsonProperty("refunded") REFUNDED
}
