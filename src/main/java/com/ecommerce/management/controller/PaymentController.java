package com.ecommerce.management.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.management.dto.common.DataResponse;
import com.ecommerce.management.dto.payment.PaymentResponse;
import com.ecommerce.management.service.PaymentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/{id}/refund")
    public ResponseEntity<DataResponse<PaymentResponse>> refund(@PathVariable Long id) {
        return ResponseEntity.ok(new DataResponse<>(paymentService.refund(id)));
    }
}
