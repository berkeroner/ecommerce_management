package com.ecommerce.management.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;

import com.ecommerce.management.dto.order.OrderRequest;
import com.ecommerce.management.dto.order.OrderResponse;
import com.ecommerce.management.dto.order.OrderStatusResponse;
import com.ecommerce.management.dto.payment.PaymentRequest;
import com.ecommerce.management.dto.payment.PaymentResponse;
import com.ecommerce.management.entity.enums.OrderStatus;
import com.ecommerce.management.entity.enums.PaymentMethod;
import com.ecommerce.management.entity.enums.PaymentStatus;
import com.ecommerce.management.service.OrderService;
import com.ecommerce.management.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private OrderService orderService;
    @MockitoBean private PaymentService paymentService;

    private static final String REQUEST = """
            {
              "customer_id": 1,
              "payment_method": "credit_card",
              "shipping_provider": "mock",
              "items": [{"product_id": 10, "quantity": 2}],
              "shipping_address": {
                "title": "Ev", "city": "İstanbul", "district": "Kadıköy",
                "address_line": "Test Sokak No: 1", "postal_code": "34710"
              }
            }
            """;

    @Test
    void shouldCreateOrderAndBindSnakeCaseRequest() throws Exception {
        when(orderService.createOrder(any(OrderRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.order_no").value("ORD-TEST"))
                .andExpect(jsonPath("$.data.status").value("processing"))
                .andExpect(jsonPath("$.data.total_amount").value(500))
                .andExpect(jsonPath("$.data.currency").value("TRY"));

        ArgumentCaptor<OrderRequest> captor = ArgumentCaptor.forClass(OrderRequest.class);
        verify(orderService).createOrder(captor.capture());
        OrderRequest request = captor.getValue();
        assertEquals(1L, request.customerId());
        assertEquals(PaymentMethod.CREDIT_CARD, request.paymentMethod());
        assertEquals("mock", request.shippingProvider());
        assertEquals(1, request.items().size());
        assertEquals(10L, request.items().get(0).productId());
        assertEquals(2, request.items().get(0).quantity());
        assertEquals("Test Sokak No: 1", request.shippingAddress().addressLine());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"customer_id\": 0",
            "\"payment_method\": null",
            "\"shipping_provider\": \"\"",
            "\"product_id\": 0",
            "\"quantity\": 0",
            "\"city\": \"\""
    })
    void shouldRejectInvalidRequestWithoutCallingService(String replacement) throws Exception {
        String field = replacement.substring(0, replacement.indexOf(':'));
        String invalid = REQUEST.replaceFirst(field + ": [^,}]+", replacement);

        mockMvc.perform(post("/api/v1/orders")
                        .header("X-Correlation-ID", "order-validation-test")
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isNotEmpty())
                .andExpect(jsonPath("$.error.correlation_id").value("order-validation-test"));
        verifyNoInteractions(orderService);
    }

    @Test
    void shouldRejectEmptyItems() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST.replace(
                                "[{\"product_id\": 10, \"quantity\": 2}]", "[]")))
                .andExpect(status().is(422));
        verifyNoInteractions(orderService);
    }

    @Test
    void shouldRejectMissingShippingAddress() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customer_id": 1, "payment_method": "credit_card",
                                 "shipping_provider": "mock",
                                 "items": [{"product_id": 10, "quantity": 2}]}
                                """))
                .andExpect(status().is(422));
        verifyNoInteractions(orderService);
    }

    @Test
    void shouldReturnOrderDetail() throws Exception {
        when(orderService.findById(100L)).thenReturn(response());
        mockMvc.perform(get("/api/v1/orders/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.order_no").value("ORD-TEST"));
        verify(orderService).findById(100L);
    }

    @Test
    void shouldReturnOrderStatus() throws Exception {
        when(orderService.getStatus(100L)).thenReturn(
                new OrderStatusResponse(100L, OrderStatus.PROCESSING));
        mockMvc.perform(get("/api/v1/orders/100/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("processing"));
        verify(orderService).getStatus(100L);
    }

    @Test
    void shouldCancelOrder() throws Exception {
        when(orderService.cancelOrder(100L)).thenReturn(new OrderResponse(
                100L,
                "ORD-TEST",
                OrderStatus.CANCELLED,
                new BigDecimal("500.00"),
                "TRY"
        ));

        mockMvc.perform(post("/api/v1/orders/100/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.order_no").value("ORD-TEST"))
                .andExpect(jsonPath("$.data.status").value("cancelled"))
                .andExpect(jsonPath("$.data.total_amount").value(500));
        verify(orderService).cancelOrder(100L);
    }

    @Test
    void shouldStartPayment() throws Exception {
        when(paymentService.startPayment(eq(100L), any(PaymentRequest.class)))
                .thenReturn(new PaymentResponse(5L, "PAY-TEST", 100L,
                        PaymentMethod.CREDIT_CARD, "dummy-payment-service", PaymentStatus.PROCESSING,
                        new BigDecimal("500.00"), "TXN-TEST", null, null));

        mockMvc.perform(post("/api/v1/orders/100/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"credit_card\",\"payment_token\":\"mock-token\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.status").value("processing"))
                .andExpect(jsonPath("$.data.payment_no").value("PAY-TEST"));
        verify(paymentService).startPayment(eq(100L), any(PaymentRequest.class));
    }

    @Test
    void shouldRejectPaymentWithoutMethod() throws Exception {
        mockMvc.perform(post("/api/v1/orders/100/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payment_token\":\"mock-token\"}"))
                .andExpect(status().is(422));
        verifyNoInteractions(paymentService);
    }

    @Test
    void shouldReturnConflictWhenOrderCannotBeCancelled() throws Exception {
        when(orderService.cancelOrder(100L)).thenThrow(new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Order cannot be cancelled from status: FAILED"
        ));

        mockMvc.perform(post("/api/v1/orders/100/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void shouldReturnNotFoundWhenCancelledOrderDoesNotExist() throws Exception {
        when(orderService.cancelOrder(999L)).thenThrow(new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Order not found: 999"
        ));

        mockMvc.perform(post("/api/v1/orders/999/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/status"})
    void shouldReturnNotFound(String suffix) throws Exception {
        ResponseStatusException missing = new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Order not found: 999");
        if (suffix.isEmpty()) {
            when(orderService.findById(999L)).thenThrow(missing);
        } else {
            when(orderService.getStatus(999L)).thenThrow(missing);
        }
        mockMvc.perform(get("/api/v1/orders/999" + suffix))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Order not found: 999"));
    }

    @Test
    void shouldReturnStockConflict() throws Exception {
        when(orderService.createOrder(any(OrderRequest.class))).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock"));
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void shouldRejectMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        verifyNoInteractions(orderService);
    }

    private OrderResponse response() {
        return new OrderResponse(100L, "ORD-TEST", OrderStatus.PROCESSING,
                new BigDecimal("500.00"), "TRY");
    }
}
