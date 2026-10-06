package com.ecommerce.management.service;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!cli & !test")
public class PaymentStatusPollingScheduler {

    private final PaymentStatusReconciler reconciler;

    public PaymentStatusPollingScheduler(PaymentStatusReconciler reconciler) {
        this.reconciler = reconciler;
    }

    @Scheduled(fixedDelayString = "${payment.status-poll-interval:30s}")
    public void pollPaymentStatuses() {
        reconciler.reconcile();
    }
}
