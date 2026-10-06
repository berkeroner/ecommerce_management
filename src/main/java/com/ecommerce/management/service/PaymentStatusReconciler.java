package com.ecommerce.management.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.client.DummyPaymentClient;
import com.ecommerce.management.dto.payment.provider.DummyPaymentAcceptedResponse;
import com.ecommerce.management.dto.payment.provider.ProviderPaymentStatus;
import com.ecommerce.management.dto.payment.provider.ProviderPaymentStatusResponse;
import com.ecommerce.management.entity.enums.PaymentStatus;
import com.ecommerce.management.repository.PaymentRepository;
import com.ecommerce.management.repository.PaymentRepository.PollingCandidate;

@Service
public class PaymentStatusReconciler {

    private static final Logger log = LoggerFactory.getLogger(PaymentStatusReconciler.class);
    private static final EnumSet<PaymentStatus> POLLABLE_STATUSES = EnumSet.of(
            PaymentStatus.PENDING, PaymentStatus.PROCESSING);
    private static final int BATCH_SIZE = 100;

    private final PaymentRepository paymentRepository;
    private final DummyPaymentClient paymentClient;
    private final PaymentInitiationService paymentInitiationService;
    private final PaymentService paymentService;

    public PaymentStatusReconciler(
            PaymentRepository paymentRepository,
            DummyPaymentClient paymentClient,
            PaymentInitiationService paymentInitiationService,
            PaymentService paymentService) {
        this.paymentRepository = paymentRepository;
        this.paymentClient = paymentClient;
        this.paymentInitiationService = paymentInitiationService;
        this.paymentService = paymentService;
    }

    public void reconcile() {
        long afterId = 0L;
        while (true) {
            List<PollingCandidate> candidates = paymentRepository.findStatusPollingCandidates(
                    POLLABLE_STATUSES, afterId, PageRequest.of(0, BATCH_SIZE));
            if (candidates.isEmpty()) {
                return;
            }
            candidates.forEach(this::reconcileCandidate);
            afterId = candidates.getLast().getId();
        }
    }

    private void reconcileCandidate(PollingCandidate candidate) {
        try {
            Optional<ProviderPaymentStatusResponse> providerPayment =
                    candidate.getTransactionId() == null
                            ? paymentClient.findPaymentByIdempotencyKey(
                                    UUID.fromString(candidate.getIdempotencyKey()))
                            : paymentClient.findPayment(
                                    UUID.fromString(candidate.getTransactionId()));
            if (providerPayment.isEmpty()) {
                return;
            }

            ProviderPaymentStatusResponse response = providerPayment.get();
            if (candidate.getStatus() == PaymentStatus.PENDING) {
                paymentInitiationService.recordProviderAcceptance(
                        UUID.fromString(candidate.getIdempotencyKey()),
                        new DummyPaymentAcceptedResponse(
                                response.paymentId(),
                                response.orderId(),
                                response.idempotencyKey(),
                                ProviderPaymentStatus.PROCESSING));
            }

            if (response.status() == ProviderPaymentStatus.APPROVED
                    || response.status() == ProviderPaymentStatus.REJECTED) {
                paymentService.handleCallback(response.toCallbackRequest());
            }
        } catch (IllegalArgumentException | ResponseStatusException exception) {
            log.warn("Payment status polling failed for local paymentId={}: {}",
                    candidate.getId(), exception.getMessage());
        }
    }
}
