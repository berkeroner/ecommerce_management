package com.ecommerce.management.dto.order;

import com.ecommerce.management.entity.enums.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record OrderRequest(

        @NotNull
        @Positive
        @JsonProperty("customer_id")
        Long customerId,

        @NotNull
        @JsonProperty("payment_method")
        PaymentMethod paymentMethod,

        @NotBlank
        @JsonProperty("shipping_provider")
        String shippingProvider,

        @NotNull
        @Positive
        @JsonProperty("shipping_address_id")
        Long shippingAddressId,

        @Positive
        @JsonProperty("billing_address_id")
        Long billingAddressId,

        @NotEmpty
        @Valid
        List<OrderItemRequest> items

) {
}
