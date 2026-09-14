package com.ecommerce.management.entity.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AddressType {
    @JsonProperty("billing") BILLING,
    @JsonProperty("shipping") SHIPPING
}
