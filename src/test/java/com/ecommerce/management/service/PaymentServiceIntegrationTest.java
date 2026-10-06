package com.ecommerce.management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.client.DummyPaymentClient;
import com.ecommerce.management.dto.payment.PaymentRequest;
import com.ecommerce.management.dto.payment.PaymentResponse;
import com.ecommerce.management.dto.payment.provider.DummyPaymentAcceptedResponse;
import com.ecommerce.management.dto.payment.provider.DummyRefundResponse;
import com.ecommerce.management.dto.payment.provider.PaymentCallbackRequest;
import com.ecommerce.management.dto.payment.provider.ProviderPaymentStatus;
import com.ecommerce.management.entity.Customer;
import com.ecommerce.management.entity.Order;
import com.ecommerce.management.entity.OrderItem;
import com.ecommerce.management.entity.enums.OrderStatus;
import com.ecommerce.management.entity.enums.PaymentMethod;
import com.ecommerce.management.entity.enums.PaymentStatus;
import com.ecommerce.management.entity.enums.RecordStatus;
import com.ecommerce.management.repository.CustomerRepository;
import com.ecommerce.management.repository.OrderRepository;
import com.ecommerce.management.repository.OrderItemRepository;
import com.ecommerce.management.repository.OrderStatusHistoryRepository;
import com.ecommerce.management.repository.OutboxEventRepository;
import com.ecommerce.management.repository.PaymentRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:payment-service;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class PaymentServiceIntegrationTest {

    @Autowired PaymentService paymentService;
    @Autowired CustomerRepository customerRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired OrderStatusHistoryRepository historyRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired PaymentTimeoutReconciler paymentTimeoutReconciler;
    @MockitoBean DummyPaymentClient dummyPaymentClient;
    @MockitoBean OrderItemRepository orderItemRepository;

    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAll();
        historyRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void shouldCompleteAndRefundCardPaymentWithTransactionalRecords() {
        LocalDateTime now = LocalDateTime.now();
        Customer customer = customerRepository.save(customer(now));
        Order order = orderRepository.save(order(customer, now));

        UUID providerPaymentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        OrderItem item = new OrderItem();
        item.setProductName("Integration product");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("250.00"));
        when(orderItemRepository.findAllByOrderId(order.getId())).thenReturn(List.of(item));
        when(dummyPaymentClient.createPayment(any(), any())).thenAnswer(invocation ->
                new DummyPaymentAcceptedResponse(
                        providerPaymentId,
                        order.getId().toString(),
                        invocation.getArgument(0),
                        ProviderPaymentStatus.PROCESSING));
        when(dummyPaymentClient.refundPayment(any(), any()))
                .thenReturn(new DummyRefundResponse("REF-INTEGRATION"));

        PaymentResponse processing = paymentService.startPayment(order.getId(),
                new PaymentRequest(PaymentMethod.CREDIT_CARD, "secret-token"));

        assertEquals(PaymentStatus.PROCESSING, processing.status());
        assertEquals(OrderStatus.PROCESSING,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
        UUID idempotencyKey = UUID.fromString(paymentRepository.findById(processing.id())
                .orElseThrow().getIdempotencyKey());

        PaymentResponse completed = paymentService.handleCallback(new PaymentCallbackRequest(
                providerPaymentId,
                order.getId().toString(),
                idempotencyKey,
                ProviderPaymentStatus.APPROVED,
                new BigDecimal("500.00"),
                "TRY",
                "Payment approved"
        ));

        assertEquals(PaymentStatus.COMPLETED, completed.status());
        assertEquals(OrderStatus.CONFIRMED,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(1, paymentRepository.count());
        assertEquals(1, historyRepository.count());
        assertEquals(3, outboxEventRepository.count());
        assertFalse(outboxEventRepository.findAll().stream()
                .anyMatch(event -> event.getPayload().contains("secret-token")));

        PaymentResponse refunded = paymentService.refund(completed.id());
        assertEquals(PaymentStatus.REFUNDED, refunded.status());
        assertEquals(4, outboxEventRepository.count());
    }

    @Test
    void shouldBlockWhileProcessingAndAllowAnotherAttemptAfterRejection() {
        LocalDateTime now = LocalDateTime.now();
        Customer customer = customerRepository.save(customer(now));
        Order order = orderRepository.save(order(customer, now));

        OrderItem item = new OrderItem();
        item.setProductName("Retry product");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("250.00"));
        when(orderItemRepository.findAllByOrderId(order.getId())).thenReturn(List.of(item));
        when(dummyPaymentClient.createPayment(any(), any())).thenAnswer(invocation ->
                new DummyPaymentAcceptedResponse(
                        UUID.randomUUID(),
                        order.getId().toString(),
                        invocation.getArgument(0),
                        ProviderPaymentStatus.PROCESSING));

        PaymentResponse firstAttempt = paymentService.startPayment(order.getId(),
                new PaymentRequest(PaymentMethod.CREDIT_CARD, "secret-token"));
        assertEquals(PaymentStatus.PROCESSING, firstAttempt.status());

        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
                () -> paymentService.startPayment(order.getId(),
                        new PaymentRequest(PaymentMethod.CREDIT_CARD, "secret-token")));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());

        var firstPayment = paymentRepository.findById(firstAttempt.id()).orElseThrow();
        paymentService.handleCallback(new PaymentCallbackRequest(
                UUID.fromString(firstPayment.getTransactionId()),
                order.getId().toString(),
                UUID.fromString(firstPayment.getIdempotencyKey()),
                ProviderPaymentStatus.REJECTED,
                new BigDecimal("500.00"),
                "TRY",
                "Declined"));

        PaymentResponse retry = paymentService.startPayment(order.getId(),
                new PaymentRequest(PaymentMethod.CREDIT_CARD, "new-token"));

        assertEquals(PaymentStatus.PROCESSING, retry.status());
        assertEquals(OrderStatus.PROCESSING,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(2, paymentRepository.count());
    }

    @Test
    void shouldFailTimedOutPaymentAndAllowRetry() {
        LocalDateTime now = LocalDateTime.now();
        Customer customer = customerRepository.save(customer(now));
        Order order = orderRepository.save(order(customer, now));

        OrderItem item = new OrderItem();
        item.setProductName("Timeout product");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("250.00"));
        when(orderItemRepository.findAllByOrderId(order.getId())).thenReturn(List.of(item));
        when(dummyPaymentClient.createPayment(any(), any())).thenAnswer(invocation ->
                new DummyPaymentAcceptedResponse(
                        UUID.randomUUID(),
                        order.getId().toString(),
                        invocation.getArgument(0),
                        ProviderPaymentStatus.PROCESSING));

        PaymentResponse firstAttempt = paymentService.startPayment(order.getId(),
                new PaymentRequest(PaymentMethod.CREDIT_CARD, "secret-token"));
        var stalePayment = paymentRepository.findById(firstAttempt.id()).orElseThrow();
        stalePayment.setUpdatedAt(LocalDateTime.now().minusMinutes(10));
        paymentRepository.saveAndFlush(stalePayment);

        paymentTimeoutReconciler.reconcile();

        var timedOutPayment = paymentRepository.findById(firstAttempt.id()).orElseThrow();
        assertEquals(PaymentStatus.FAILED, timedOutPayment.getStatus());
        assertEquals("Payment provider callback timed out", timedOutPayment.getFailureReason());

        PaymentResponse retry = paymentService.startPayment(order.getId(),
                new PaymentRequest(PaymentMethod.CREDIT_CARD, "new-token"));
        assertEquals(PaymentStatus.PROCESSING, retry.status());
        assertEquals(2, paymentRepository.count());
    }

    private Customer customer(LocalDateTime now) {
        Customer customer = new Customer();
        customer.setName("Payment Test Customer");
        customer.setEmail("payment-test@example.com");
        customer.setStatus(RecordStatus.ACTIVE);
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        return customer;
    }

    private Order order(Customer customer, LocalDateTime now) {
        Order order = new Order();
        order.setOrderNo("ORD-PAYMENT-TEST");
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PROCESSING);
        order.setCurrency("TRY");
        order.setSubtotal(new BigDecimal("500.00"));
        order.setDiscountTotal(BigDecimal.ZERO);
        order.setShippingTotal(BigDecimal.ZERO);
        order.setGrandTotal(new BigDecimal("500.00"));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        return order;
    }
}
