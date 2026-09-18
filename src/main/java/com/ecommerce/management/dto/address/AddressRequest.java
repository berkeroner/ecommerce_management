package com.ecommerce.management.dto.address;

import com.ecommerce.management.entity.enums.AddressType;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddressRequest(

        @NotNull
        @JsonProperty("address_type")
        AddressType addressType,

        @NotBlank @Size(max = 100) String title,

        @NotBlank @Size(max = 100) String city,

        @NotBlank @Size(max = 100) String district,

        @NotBlank
        @JsonProperty("address_line")
        String addressLine,

        @Size(max = 20)
        @JsonProperty("postal_code")
        String postalCode
) {
}
