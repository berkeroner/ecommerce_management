package com.ecommerce.management.payment;

import java.math.BigDecimal;

import com.ecommerce.management.entity.Payment;
import com.ecommerce.management.entity.enums.PaymentMethod;

public interface PaymentStrategy {

    boolean supports(PaymentMethod method);

    String provider();

    PaymentResult pay(String paymentToken, BigDecimal amount, String currency);

    String refund(Payment payment);
}
