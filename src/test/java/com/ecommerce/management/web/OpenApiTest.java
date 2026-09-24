package com.ecommerce.management.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiTest {
    @Autowired MockMvc mvc;

    @Test
    void documentationIncludesControllersAndUsesSnakeCaseSchemas() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Mini E-Ticaret API"))
                .andExpect(jsonPath("$.paths['/api/v1/products'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/categories'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/customers'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/customers/{customerId}/addresses'].post").exists())
                .andExpect(jsonPath("$.components.schemas.AddressRequest.properties.address_type").exists())
                .andExpect(jsonPath("$.components.schemas.AddressRequest.properties.address_line").exists())
                .andExpect(jsonPath("$.components.schemas.CategoryRequest.properties.name").exists())
                .andExpect(jsonPath("$.components.schemas.CategoryRequest.properties.slug").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CategoryRequest.properties.is_active").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CategoryRequest.properties.id").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CategoryRequest.properties.created_at").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CategoryRequest.properties.updated_at").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CustomerRequest.properties.status").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CustomerRequest.properties.id").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CustomerRequest.properties.created_at").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CustomerRequest.properties.updated_at").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ProductRequest.properties.category_id").exists())
                .andExpect(jsonPath("$.components.schemas.ProductRequest.properties.status").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ProductRequest.properties.id").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ProductRequest.properties.created_at").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ProductRequest.properties.updated_at").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.OrderRequest.properties.customer_id").exists())
                .andExpect(jsonPath("$.components.schemas.OrderRequest.properties.payment_method").exists())
                .andExpect(jsonPath("$.components.schemas.OrderRequest.properties.shipping_provider").exists())
                .andExpect(jsonPath("$.components.schemas.OrderRequest.properties.shipping_address_id").exists())
                .andExpect(jsonPath("$.components.schemas.OrderRequest.properties.billing_address_id").exists())
                .andExpect(jsonPath("$.components.schemas.OrderItemRequest.properties.product_id").exists())
                .andExpect(jsonPath("$.components.schemas.OrderRequest.properties.shipping_address").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.OrderRequest.properties.order_no").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.OrderRequest.properties.status").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.OrderRequest.properties.currency").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.OrderRequest.properties.subtotal").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.OrderRequest.properties.grand_total").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PaymentRequest.properties.payment_token").exists())
                .andExpect(jsonPath("$.components.schemas.PaymentRequest.properties.payment_no").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PaymentRequest.properties.provider").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PaymentRequest.properties.status").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PaymentRequest.properties.amount").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PaymentRequest.properties.transaction_id").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AddressRequest.properties.id").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AddressRequest.properties.addressable_id").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AddressRequest.properties.created_at").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AddressRequest.properties.updated_at").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.OrderResponse.properties.order_no").exists())
                .andExpect(jsonPath("$.components.schemas.OrderResponse.properties.total_amount").exists())
                .andExpect(jsonPath("$.components.schemas.PaymentResponse.properties.transaction_id").exists())
                .andExpect(jsonPath("$.components.schemas.PageResponseProductResponse.properties.total_elements").exists())
                .andExpect(jsonPath("$.components.schemas.OrderRequest.properties.customerId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.OrderRequest.properties.paymentMethod").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PaymentRequest.properties.paymentToken").doesNotExist());
    }

    @Test
    void swaggerUiAndItsConfigurationAreAvailable() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("/v3/api-docs"));
    }
}
