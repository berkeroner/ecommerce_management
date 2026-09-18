package com.ecommerce.management.dto.order;

import com.ecommerce.management.entity.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record OrderRequest(

        @NotNull
        @Positive
        Long customerId,

        @NotNull
        PaymentMethod paymentMethod,

        @NotBlank
        String shippingProvider,

        @NotEmpty
        @Valid
        List<OrderItemRequest> items,

        @NotNull
        @Valid
        OrderShippingAddressRequest shippingAddress

) {
}