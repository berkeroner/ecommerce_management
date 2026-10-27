package com.ecommerce.management.payment;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ecommerce.management.entity.enums.PaymentMethod;

@Component
public class PaymentStrategyFactory {

    private final Map<PaymentMethod, PaymentStrategy> strategies;

    public PaymentStrategyFactory(List<PaymentStrategy> strategies) {
        Map<PaymentMethod, PaymentStrategy> byMethod = new EnumMap<>(PaymentMethod.class);
        for (PaymentStrategy strategy : strategies) {
            PaymentStrategy duplicate = byMethod.put(strategy.method(), strategy);
            if (duplicate != null) {
                throw new IllegalStateException(
                        "Multiple payment strategies configured for " + strategy.method());
            }
        }
        this.strategies = Map.copyOf(byMethod);
    }

    public PaymentStrategy get(PaymentMethod method) {
        PaymentStrategy strategy = strategies.get(method);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported payment method: " + method);
        }
        return strategy;
    }
}
