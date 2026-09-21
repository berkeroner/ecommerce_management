package com.ecommerce.management.payment;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ecommerce.management.entity.Payment;
import com.ecommerce.management.entity.enums.PaymentMethod;

@Component
public class BankTransferPaymentStrategy implements PaymentStrategy {
    public boolean supports(PaymentMethod method) { return method == PaymentMethod.BANK_TRANSFER; }
    public String provider() { return "mock-bank"; }
    public PaymentResult pay(String token, BigDecimal amount, String currency) {
        return PaymentResult.pending("TRANSFER-" + UUID.randomUUID());
    }
    public String refund(Payment payment) { return "REF-" + UUID.randomUUID(); }
}
