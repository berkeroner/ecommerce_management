package com.ecommerce.management.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ecommerce.management.entity.enums.PaymentStatus;
import com.ecommerce.management.repository.PaymentRepository;

@Component
public class PaymentTimeoutReconciler {

    private static final EnumSet<PaymentStatus> RECONCILABLE_STATUSES = EnumSet.of(
            PaymentStatus.PENDING, PaymentStatus.PROCESSING);
    private static final int BATCH_SIZE = 100;

    private final PaymentRepository paymentRepository;
    private final PaymentInitiationService paymentInitiationService;
    private final Duration processingTimeout;

    public PaymentTimeoutReconciler(
            PaymentRepository paymentRepository,
            PaymentInitiationService paymentInitiationService,
            @Value("${payment.processing-timeout:5m}") Duration processingTimeout) {
        this.paymentRepository = paymentRepository;
        this.paymentInitiationService = paymentInitiationService;
        this.processingTimeout = processingTimeout;
    }

    @Scheduled(fixedDelayString = "${payment.processing-timeout-scan-interval:30s}")
    public void reconcile() {
        LocalDateTime cutoff = LocalDateTime.now().minus(processingTimeout);
        paymentRepository.findTimedOutPaymentIds(
                        RECONCILABLE_STATUSES, cutoff, PageRequest.of(0, BATCH_SIZE))
                .forEach(paymentId ->
                        paymentInitiationService.expireTimedOut(paymentId, cutoff));
    }
}
