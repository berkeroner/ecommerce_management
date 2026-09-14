package com.ecommerce.management.entity.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum OutboxStatus {
    @JsonProperty("pending") PENDING,
    @JsonProperty("published") PUBLISHED,
    @JsonProperty("failed") FAILED
}
