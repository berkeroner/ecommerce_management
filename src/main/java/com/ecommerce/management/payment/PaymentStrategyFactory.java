package com.ecommerce.management.payment;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.entity.enums.PaymentMethod;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentStrategyFactory {

    private final List<PaymentStrategy> strategies;

    public PaymentStrategy get(PaymentMethod method) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(method))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.valueOf(422), "Unsupported payment method: " + method));
    }
}
