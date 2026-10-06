package com.ecommerce.management.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.client.DummyPaymentClient;
import com.ecommerce.management.dto.payment.PaymentRequest;
import com.ecommerce.management.dto.payment.PaymentResponse;
import com.ecommerce.management.dto.payment.provider.DummyPaymentAcceptedResponse;
import com.ecommerce.management.dto.payment.provider.DummyRefundResponse;
import com.ecommerce.management.dto.payment.provider.PaymentCallbackRequest;
import com.ecommerce.management.dto.payment.provider.ProviderPaymentStatus;
import com.ecommerce.management.entity.Order;
import com.ecommerce.management.entity.OrderStatusHistory;
import com.ecommerce.management.entity.OutboxEvent;
import com.ecommerce.management.entity.Payment;
import com.ecommerce.management.entity.enums.OrderStatus;
import com.ecommerce.management.entity.enums.OutboxStatus;
import com.ecommerce.management.entity.enums.PaymentStatus;
import com.ecommerce.management.repository.OrderRepository;
import com.ecommerce.management.repository.OrderStatusHistoryRepository;
import com.ecommerce.management.repository.OutboxEventRepository;
import com.ecommerce.management.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final DummyPaymentClient dummyPaymentClient;
    private final PaymentInitiationService paymentInitiationService;
    private final ObjectMapper objectMapper;

    public PaymentResponse startPayment(Long orderId, PaymentRequest request) {
        var pending = paymentInitiationService.createPending(orderId, request);
        try {
            DummyPaymentAcceptedResponse providerResponse = dummyPaymentClient.createPayment(
                    pending.idempotencyKey(), pending.providerRequest());
            return paymentInitiationService.recordProviderAcceptance(
                    pending.idempotencyKey(), providerResponse);
        } catch (RuntimeException exception) {
            paymentInitiationService.markSubmissionFailed(
                    pending.idempotencyKey(), providerFailureReason(exception));
            throw exception;
        }
    }

    @Transactional
    public PaymentResponse handleCallback(PaymentCallbackRequest callback) {
        if (callback.status() != ProviderPaymentStatus.APPROVED
                && callback.status() != ProviderPaymentStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Callback must contain a final payment status");
        }

        Payment payment = paymentRepository
                .findByIdempotencyKeyForUpdate(callback.idempotencyKey().toString())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found for idempotency key: " + callback.idempotencyKey()
                ));
        Order order = payment.getOrder();
        validateCallback(callback, payment, order);

        if (callback.status() == ProviderPaymentStatus.APPROVED
                && payment.getStatus() == PaymentStatus.COMPLETED) {
            return toResponse(payment);
        }
        if (callback.status() == ProviderPaymentStatus.REJECTED
                && payment.getStatus() == PaymentStatus.FAILED) {
            return toResponse(payment);
        }
        if (!EnumSet.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING)
                .contains(payment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment callback conflicts with current status: " + payment.getStatus());
        }
        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment callback cannot update order status: " + order.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        if (callback.status() == ProviderPaymentStatus.APPROVED) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setFailureReason(null);
            payment.setPaidAt(now);
            payment.setUpdatedAt(now);
            paymentRepository.save(payment);

            transitionOrder(order, OrderStatus.CONFIRMED, "Payment completed", now);
            savePaymentEvent(payment, "payment.completed", Map.of(
                    "order_id", order.getId(),
                    "transaction_id", payment.getTransactionId()), now);
            saveOrderEvent(order, "order.confirmed", now);
        } else {
            String failureReason = callback.message() == null || callback.message().isBlank()
                    ? "Payment rejected"
                    : callback.message();
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(failureReason);
            payment.setUpdatedAt(now);
            paymentRepository.save(payment);

            savePaymentEvent(payment, "payment.failed", Map.of(
                    "order_id", order.getId(),
                    "idempotency_key", callback.idempotencyKey(),
                    "reason", failureReason), now);
        }

        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse refund(Long paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return toResponse(payment);
        }
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment cannot be refunded from status: " + payment.getStatus());
        }

        UUID providerPaymentId;
        try {
            providerPaymentId = UUID.fromString(payment.getTransactionId());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Payment does not have a valid provider payment id");
        }
        DummyRefundResponse providerResponse = dummyPaymentClient.refundPayment(
                providerPaymentId, payment.getMethod());
        if (providerResponse == null
                || providerResponse.refundId() == null
                || providerResponse.refundId().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Payment provider returned an invalid refund result");
        }
        LocalDateTime now = LocalDateTime.now();
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setUpdatedAt(now);
        paymentRepository.save(payment);

        savePaymentEvent(payment, "payment.refunded", Map.of(
                "order_id", payment.getOrder().getId(),
                "refund_transaction_id", providerResponse.refundId()), now);
        return toResponse(payment);
    }

    private void validateCallback(PaymentCallbackRequest callback, Payment payment, Order order) {
        if (!payment.getIdempotencyKey().equals(callback.idempotencyKey().toString())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Callback idempotency key does not match the payment");
        }
        if (payment.getTransactionId() != null
                && !payment.getTransactionId().equals(callback.paymentId().toString())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Callback provider payment id does not match the payment");
        }
        if (!order.getId().toString().equals(callback.orderId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Callback order id does not match the payment");
        }
        if (payment.getAmount().compareTo(callback.totalAmount()) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Callback total amount does not match the payment");
        }
        if (!order.getCurrency().equals(callback.currency())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Callback currency does not match the order");
        }
        if (payment.getTransactionId() == null) {
            payment.setTransactionId(callback.paymentId().toString());
        }
    }

    private String providerFailureReason(RuntimeException exception) {
        if (exception instanceof ResponseStatusException statusException
                && statusException.getReason() != null) {
            return statusException.getReason();
        }
        return "Payment provider request failed";
    }

    private void transitionOrder(Order order, OrderStatus newStatus, String reason, LocalDateTime now)
    {
        OrderStatus previousStatus = order.getStatus();
        order.setStatus(newStatus);
        order.setUpdatedAt(now);
        orderRepository.save(order);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setReason(reason);
        history.setCreatedAt(now);
        orderStatusHistoryRepository.save(history);
    }

    private void savePaymentEvent(Payment payment, String eventType, Map<String, Object> extraData, LocalDateTime now)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("payment_id", payment.getId());
        data.put("payment_no", payment.getPaymentNo());
        data.put("status", payment.getStatus());
        data.putAll(extraData);
        saveEvent("payment", payment.getId(), eventType, data, now);
    }

    private void saveOrderEvent(Order order, String eventType, LocalDateTime now) {
        saveEvent("order", order.getId(), eventType, Map.of(
                "order_id", order.getId(),
                "customer_id", order.getCustomer().getId(),
                "status", order.getStatus()), now);
    }

    private void saveEvent(String aggregateType, Long aggregateId, String eventType,
                           Map<String, Object> data, LocalDateTime now) {
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
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
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
}
