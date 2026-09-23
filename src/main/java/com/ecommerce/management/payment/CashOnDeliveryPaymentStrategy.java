package com.ecommerce.management.payment;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ecommerce.management.entity.Payment;
import com.ecommerce.management.entity.enums.PaymentMethod;

@Component
public class CashOnDeliveryPaymentStrategy implements PaymentStrategy {

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CASH_ON_DELIVERY;
    }

    @Override
    public String provider() {
        return "cash-on-delivery";
    }

    @Override
    public PaymentResult pay(String token, BigDecimal amount, String currency) {
        return PaymentResult.pending("COD-" + UUID.randomUUID());
    }

    @Override
    public String refund(Payment payment) {
        return "REF-" + UUID.randomUUID();
    }
}
