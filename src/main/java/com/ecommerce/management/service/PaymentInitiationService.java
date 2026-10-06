package com.ecommerce.management.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.dto.payment.PaymentRequest;
import com.ecommerce.management.dto.payment.PaymentResponse;
import com.ecommerce.management.dto.payment.provider.DummyPaymentAcceptedResponse;
import com.ecommerce.management.dto.payment.provider.DummyPaymentItemRequest;
import com.ecommerce.management.dto.payment.provider.DummyPaymentRequest;
import com.ecommerce.management.dto.payment.provider.ProviderPaymentStatus;
import com.ecommerce.management.entity.Order;
import com.ecommerce.management.entity.OutboxEvent;
import com.ecommerce.management.entity.Payment;
import com.ecommerce.management.entity.enums.OrderStatus;
import com.ecommerce.management.entity.enums.OutboxStatus;
import com.ecommerce.management.entity.enums.PaymentStatus;
import com.ecommerce.management.repository.OrderItemRepository;
import com.ecommerce.management.repository.OrderRepository;
import com.ecommerce.management.repository.OutboxEventRepository;
import com.ecommerce.management.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PaymentInitiationService {

    private static final EnumSet<PaymentStatus> BLOCKING_STATUSES = EnumSet.of(
            PaymentStatus.PENDING, PaymentStatus.PROCESSING, PaymentStatus.COMPLETED);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PendingPaymentAttempt createPending(Long orderId, PaymentRequest request) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment cannot be started for order status: " + order.getStatus());
        }
        if (paymentRepository.existsByOrderIdAndStatusIn(orderId, BLOCKING_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order already has a pending, active or completed payment");
        }

        List<DummyPaymentItemRequest> items = orderItemRepository.findAllByOrderId(orderId)
                .stream()
                .map(item -> new DummyPaymentItemRequest(
                        item.getProductName(), item.getQuantity(), item.getUnitPrice()))
                .toList();
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order does not contain any items");
        }

        LocalDateTime now = LocalDateTime.now();
        UUID idempotencyKey = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentNo("PAY-" + UUID.randomUUID());
        payment.setMethod(request.method());
        payment.setProvider("dummy-payment-service");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(order.getGrandTotal());
        payment.setIdempotencyKey(idempotencyKey.toString());
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        payment = paymentRepository.save(payment);

        savePaymentEvent(payment, "payment.requested", Map.of(
                "order_id", order.getId(),
                "idempotency_key", idempotencyKey,
                "method", payment.getMethod(),
                "amount", payment.getAmount(),
                "currency", order.getCurrency()), now);

        return new PendingPaymentAttempt(
                idempotencyKey,
                new DummyPaymentRequest(
                        order.getId().toString(),
                        request.method(),
                        request.paymentToken(),
                        items,
                        order.getCurrency()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentResponse recordProviderAcceptance(
            UUID idempotencyKey, DummyPaymentAcceptedResponse response) {
        Payment payment = getByIdempotencyKey(idempotencyKey);
        if (response == null
                || response.paymentId() == null
                || response.idempotencyKey() == null
                || response.status() != ProviderPaymentStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment provider returned an invalid result");
        }
        if (!payment.getOrder().getId().toString().equals(response.orderId())
                || !idempotencyKey.equals(response.idempotencyKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment provider returned mismatched payment identifiers");
        }

        if (payment.getTransactionId() != null
                && !payment.getTransactionId().equals(response.paymentId().toString())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency key is already associated with another provider payment");
        }
        payment.setTransactionId(response.paymentId().toString());
        if (payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.PROCESSING);
        }
        payment.setUpdatedAt(LocalDateTime.now());
        return toResponse(paymentRepository.save(payment));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSubmissionFailed(UUID idempotencyKey, String reason) {
        Payment payment = getByIdempotencyKey(idempotencyKey);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String failureReason = reason == null || reason.isBlank()
                ? "Payment provider request failed"
                : reason;
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(failureReason);
        payment.setUpdatedAt(now);
        paymentRepository.save(payment);
        savePaymentEvent(payment, "payment.failed", Map.of(
                "order_id", payment.getOrder().getId(),
                "idempotency_key", idempotencyKey,
                "reason", failureReason), now);
    }

    private Payment getByIdempotencyKey(UUID idempotencyKey) {
        return paymentRepository.findByIdempotencyKeyForUpdate(idempotencyKey.toString())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found for idempotency key: " + idempotencyKey));
    }

    private void savePaymentEvent(
            Payment payment, String eventType, Map<String, Object> extraData, LocalDateTime now) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("payment_id", payment.getId());
        data.put("payment_no", payment.getPaymentNo());
        data.put("status", payment.getStatus());
        data.putAll(extraData);

        UUID eventId = UUID.randomUUID();
        String correlationId = MDC.get("correlation_id");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        var envelope = Map.of(
                "event_id", eventId.toString(),
                "event_type", eventType,
                "occurred_at", Instant.now().toString(),
                "correlation_id", correlationId,
                "data", data);

        OutboxEvent event = new OutboxEvent();
        event.setId(eventId);
        event.setAggregateType("payment");
        event.setAggregateId(payment.getId());
        event.setEventType(eventType);
        event.setPayload(objectMapper.writeValueAsString(envelope));
        event.setStatus(OutboxStatus.PENDING);
        event.setRetryCount(0);
        event.setCreatedAt(now);
        outboxEventRepository.save(event);
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getPaymentNo(),
                payment.getOrder().getId(), payment.getMethod(), payment.getProvider(),
                payment.getStatus(), payment.getAmount(), payment.getTransactionId(),
                payment.getFailureReason(), payment.getPaidAt());
    }

    public record PendingPaymentAttempt(
            UUID idempotencyKey,
            DummyPaymentRequest providerRequest) {
    }
}
