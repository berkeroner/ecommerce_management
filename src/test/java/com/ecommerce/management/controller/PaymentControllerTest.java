package com.ecommerce.management.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ecommerce.management.dto.payment.PaymentResponse;
import com.ecommerce.management.dto.payment.provider.PaymentCallbackRequest;
import com.ecommerce.management.entity.enums.PaymentMethod;
import com.ecommerce.management.entity.enums.PaymentStatus;
import com.ecommerce.management.service.PaymentService;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PaymentService paymentService;

    @Test
    void shouldRefundPayment() throws Exception {
        when(paymentService.refund(5L)).thenReturn(new PaymentResponse(
                5L, "PAY-TEST", 100L, PaymentMethod.CREDIT_CARD, "mock-card",
                PaymentStatus.REFUNDED, new BigDecimal("500.00"), "TXN-TEST", null, null));

        mockMvc.perform(post("/api/v1/payments/5/refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.status").value("refunded"));
        verify(paymentService).refund(5L);
    }

    @Test
    void shouldAcceptPaymentCallback() throws Exception {
        mockMvc.perform(post("/api/v1/payments/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentId": "11111111-1111-1111-1111-111111111111",
                                  "orderId": "100",
                                  "status": "APPROVED",
                                  "totalAmount": 500.00,
                                  "currency": "TRY",
                                  "message": "Payment approved"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(paymentService).handleCallback(any(PaymentCallbackRequest.class));
    }
}
