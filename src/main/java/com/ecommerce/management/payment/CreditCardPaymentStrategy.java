package com.ecommerce.management.payment;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.entity.Payment;
import com.ecommerce.management.entity.enums.PaymentMethod;

@Component
public class CreditCardPaymentStrategy implements PaymentStrategy {

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CREDIT_CARD;
    }

    @Override
    public String provider() {
        return "mock-card";
    }

    @Override
    public PaymentResult pay(String paymentToken, BigDecimal amount, String currency) {
        if (paymentToken == null || paymentToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.valueOf(422), "Payment token is required");
        }
        if ("mock-fail".equals(paymentToken)) {
            return PaymentResult.failed("Payment was declined by provider");
        }
        return PaymentResult.completed("TXN-" + UUID.randomUUID());
    }

    @Override
    public String refund(Payment payment) {
        return "REF-" + UUID.randomUUID();
    }
}
