package com.ecommerce.management.entity.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AttachableType {
    @JsonProperty("product") PRODUCT,
    @JsonProperty("order") ORDER,
    @JsonProperty("payment") PAYMENT
}
