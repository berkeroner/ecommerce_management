package com.ecommerce.management.entity.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AddressableType {
    @JsonProperty("customer") CUSTOMER,
    @JsonProperty("order") ORDER
}
