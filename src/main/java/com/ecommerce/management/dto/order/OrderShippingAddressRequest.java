package com.ecommerce.management.dto.order;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public record OrderShippingAddressRequest(

    @NotBlank
    @Size(max = 100)
    String title,

    @NotBlank
    @Size(max = 100)
    String city,

    @NotBlank
    @Size(max = 100)
    String district,

    @NotBlank
    @Size(max = 100)
    String addressLine,

    @Size (max = 20)
    String postalCode

) {
    
}
