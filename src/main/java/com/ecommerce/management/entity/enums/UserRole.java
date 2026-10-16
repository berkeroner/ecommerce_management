package com.ecommerce.management.entity.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum UserRole {
    @JsonProperty("admin") ADMIN,
    @JsonProperty("customer") CUSTOMER
}
