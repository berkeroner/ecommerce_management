package com.ecommerce.management.entity.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AttachmentType {
    @JsonProperty("image") IMAGE,
    @JsonProperty("invoice") INVOICE,
    @JsonProperty("receipt") RECEIPT
}
