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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.client.DummyPaymentClient;
import com.ecommerce.management.dto.payment.PaymentRequest;
import com.ecommerce.management.dto.payment.PaymentResponse;
import com.ecommerce.management.dto.payment.provider.DummyPaymentAcceptedResponse;
import com.ecommerce.management.dto.payment.provider.DummyPaymentItemRequest;
import com.ecommerce.management.dto.payment.provider.DummyPaymentRequest;
import com.ecommerce.management.dto.payment.provider.PaymentCallbackRequest;
import com.ecommerce.management.dto.payment.provider.ProviderPaymentStatus;
import com.ecommerce.management.entity.Order;
import com.ecommerce.management.entity.OrderStatusHistory;
import com.ecommerce.management.entity.OutboxEvent;
import com.ecommerce.management.entity.Payment;
import com.ecommerce.management.entity.enums.OrderStatus;
import com.ecommerce.management.entity.enums.OutboxStatus;
import com.ecommerce.management.entity.enums.PaymentStatus;
import com.ecommerce.management.payment.PaymentStrategy;
import com.ecommerce.management.payment.PaymentStrategyFactory;
import com.ecommerce.management.repository.OrderItemRepository;
import com.ecommerce.management.repository.OrderRepository;
import com.ecommerce.management.repository.OrderStatusHistoryRepository;
import com.ecommerce.management.repository.OutboxEventRepository;
import com.ecommerce.management.repository.PaymentRepository;
import com.ecommerce.management.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final EnumSet<PaymentStatus> ACTIVE_STATUSES = EnumSet.of(
            PaymentStatus.PENDING, PaymentStatus.PROCESSING, PaymentStatus.COMPLETED);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentStrategyFactory strategyFactory;
    private final DummyPaymentClient dummyPaymentClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentResponse startPayment(Long orderId, PaymentRequest request) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment cannot be started for order status: " + order.getStatus());
        }
        if (paymentRepository.existsByOrderIdAndStatusIn(orderId, ACTIVE_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order already has an active or completed payment");
        }

        List<DummyPaymentItemRequest> items = orderItemRepository.findAllByOrderId(orderId)
                .stream()
                .map(item -> new DummyPaymentItemRequest(
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice()
                ))
                .toList();

        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order does not contain any items");
        }

        DummyPaymentAcceptedResponse providerResponse = dummyPaymentClient.createPayment(
                new DummyPaymentRequest(
                        order.getId().toString(),
                        items,
                        order.getCurrency()
                )
        );
        validateProviderResponse(providerResponse, order);

        LocalDateTime now = LocalDateTime.now();
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentNo("PAY-" + UUID.randomUUID());
        payment.setMethod(request.method());
        payment.setProvider("dummy-payment-service");
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setAmount(order.getGrandTotal());
        payment.setTransactionId(providerResponse.paymentId().toString());
        payment.setFailureReason(null);
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        payment = paymentRepository.save(payment);

        savePaymentEvent(payment, "payment.requested", Map.of(
                "order_id", order.getId(),
                "method", payment.getMethod(),
                "amount", payment.getAmount(),
                "currency", order.getCurrency()), now);

        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse handleCallback(PaymentCallbackRequest callback) {
        if (callback.status() != ProviderPaymentStatus.APPROVED
                && callback.status() != ProviderPaymentStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Callback must contain a final payment status");
        }

        Payment payment = paymentRepository
                .findByTransactionIdForUpdate(callback.paymentId().toString())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found for provider payment id: " + callback.paymentId()
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

            releaseReservedStock(order, now);
            transitionOrder(order, OrderStatus.FAILED,
                    "Payment failed: " + failureReason, now);
            savePaymentEvent(payment, "payment.failed", Map.of(
                    "order_id", order.getId(),
                    "reason", failureReason), now);
        }

        return toResponse(payment);
    }

    private void releaseReservedStock(Order order, LocalDateTime now) {
        orderItemRepository.findAllByOrderId(order.getId()).forEach(item -> {
            int updated = productRepository.releaseStock(
                    item.getProduct().getId(), item.getQuantity(), now);
            if (updated != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Stock could not be restored for product: " + item.getProduct().getId());
            }
        });
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

        PaymentStrategy strategy = strategyFactory.get(payment.getMethod());
        String refundTransactionId = strategy.refund(payment);
        LocalDateTime now = LocalDateTime.now();
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setUpdatedAt(now);
        paymentRepository.save(payment);

        savePaymentEvent(payment, "payment.refunded", Map.of(
                "order_id", payment.getOrder().getId(),
                "refund_transaction_id", refundTransactionId), now);
        return toResponse(payment);
    }

    private void validateProviderResponse(DummyPaymentAcceptedResponse response, Order order) {
        if (response == null
                || response.paymentId() == null
                || response.status() != ProviderPaymentStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment provider returned an invalid result");
        }
        if (!order.getId().toString().equals(response.orderId())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment provider returned a mismatched order id");
        }
    }

    private void validateCallback(PaymentCallbackRequest callback, Payment payment, Order order) {
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
