package com.ecommerce.management.entity.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum RecordStatus {
    @JsonProperty("active") ACTIVE,
    @JsonProperty("passive") PASSIVE
}
