package com.ecommerce.management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.client.DummyPaymentClient;
import com.ecommerce.management.dto.payment.PaymentRequest;
import com.ecommerce.management.dto.payment.PaymentResponse;
import com.ecommerce.management.dto.payment.provider.DummyPaymentAcceptedResponse;
import com.ecommerce.management.dto.payment.provider.DummyPaymentItemRequest;
import com.ecommerce.management.dto.payment.provider.DummyPaymentRequest;
import com.ecommerce.management.dto.payment.provider.DummyRefundResponse;
import com.ecommerce.management.dto.payment.provider.PaymentCallbackRequest;
import com.ecommerce.management.dto.payment.provider.ProviderPaymentStatus;
import com.ecommerce.management.entity.Customer;
import com.ecommerce.management.entity.Order;
import com.ecommerce.management.entity.OutboxEvent;
import com.ecommerce.management.entity.Payment;
import com.ecommerce.management.entity.enums.OrderStatus;
import com.ecommerce.management.entity.enums.PaymentMethod;
import com.ecommerce.management.entity.enums.PaymentStatus;
import com.ecommerce.management.repository.OrderRepository;
import com.ecommerce.management.repository.OrderStatusHistoryRepository;
import com.ecommerce.management.repository.OutboxEventRepository;
import com.ecommerce.management.repository.PaymentRepository;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private DummyPaymentClient dummyPaymentClient;
    @Mock private PaymentInitiationService paymentInitiationService;
    @Spy private JsonMapper objectMapper = JsonMapper.builder().build();
    @InjectMocks private PaymentService paymentService;

    private Order order;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer();
        customer.setId(1L);
        order = new Order();
        order.setId(100L);
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PROCESSING);
        order.setGrandTotal(new BigDecimal("500.00"));
        order.setCurrency("TRY");
    }

    @Test
    void shouldKeepPaymentProcessingUntilCallback() {
        UUID idempotencyKey = UUID.fromString("22222222-2222-2222-2222-222222222222");
        DummyPaymentRequest providerRequest = new DummyPaymentRequest(
                "100",
                PaymentMethod.CREDIT_CARD,
                "token",
                java.util.List.of(new DummyPaymentItemRequest(
                        "Test product", 2, new BigDecimal("250.00"))),
                "TRY");
        var pending = new PaymentInitiationService.PendingPaymentAttempt(
                idempotencyKey, providerRequest);
        var accepted = new DummyPaymentAcceptedResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "100", idempotencyKey, ProviderPaymentStatus.PROCESSING);
        PaymentResponse acceptedResponse = response(
                PaymentStatus.PROCESSING, "11111111-1111-1111-1111-111111111111");
        when(paymentInitiationService.createPending(any(), any())).thenReturn(pending);
        when(dummyPaymentClient.createPayment(idempotencyKey, providerRequest))
                .thenReturn(accepted);
        when(paymentInitiationService.recordProviderAcceptance(idempotencyKey, accepted))
                .thenReturn(acceptedResponse);

        PaymentResponse response = paymentService.startPayment(
                100L, new PaymentRequest(PaymentMethod.CREDIT_CARD, "token"));

        assertEquals(PaymentStatus.PROCESSING, response.status());
        assertEquals(OrderStatus.PROCESSING, order.getStatus());
        assertEquals("11111111-1111-1111-1111-111111111111", response.transactionId());
        verify(dummyPaymentClient).createPayment(idempotencyKey, providerRequest);
    }

    @Test
    void shouldRejectPaymentFromCallbackAndAllowRetry() {
        UUID providerPaymentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID idempotencyKey = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Payment payment = payment(PaymentStatus.PENDING);
        payment.setTransactionId(providerPaymentId.toString());
        payment.setIdempotencyKey(idempotencyKey.toString());
        when(paymentRepository.findByIdempotencyKeyForUpdate(idempotencyKey.toString()))
                .thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.handleCallback(new PaymentCallbackRequest(
                providerPaymentId,
                "100",
                idempotencyKey,
                ProviderPaymentStatus.REJECTED,
                new BigDecimal("500.00"),
                "TRY",
                "declined"
        ));

        assertEquals(PaymentStatus.FAILED, response.status());
        assertEquals(OrderStatus.PROCESSING, order.getStatus());
        verify(outboxEventRepository).save(argThat(event ->
                event.getEventType().equals("payment.failed")));
    }

    @Test
    void shouldRejectDuplicatePayment() {
        when(paymentInitiationService.createPending(any(), any())).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "Payment is pending"));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> paymentService.startPayment(100L,
                        new PaymentRequest(PaymentMethod.CREDIT_CARD, "token")));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(dummyPaymentClient, never()).createPayment(any(), any());
    }

    @Test
    void shouldRefundCompletedPaymentAndBeIdempotent() {
        Payment payment = payment(PaymentStatus.COMPLETED);
        payment.setTransactionId("11111111-1111-1111-1111-111111111111");
        when(paymentRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(payment));
        when(dummyPaymentClient.refundPayment(
                UUID.fromString(payment.getTransactionId()), PaymentMethod.CREDIT_CARD))
                .thenReturn(new DummyRefundResponse("REF-1"));

        assertEquals(PaymentStatus.REFUNDED, paymentService.refund(5L).status());
        assertEquals(PaymentStatus.REFUNDED, paymentService.refund(5L).status());
        verify(dummyPaymentClient).refundPayment(
                UUID.fromString(payment.getTransactionId()), PaymentMethod.CREDIT_CARD);
        verify(outboxEventRepository).save(argThat(event ->
                event.getEventType().equals("payment.refunded")));
    }

    @Test
    void shouldRejectRefundForPendingPayment() {
        when(paymentRepository.findByIdForUpdate(5L))
                .thenReturn(Optional.of(payment(PaymentStatus.PENDING)));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> paymentService.refund(5L));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    private Payment payment(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(5L);
        payment.setPaymentNo("PAY-TEST");
        payment.setOrder(order);
        payment.setMethod(PaymentMethod.CREDIT_CARD);
        payment.setProvider("mock-card");
        payment.setStatus(status);
        payment.setAmount(new BigDecimal("500.00"));
        return payment;
    }

    private PaymentResponse response(PaymentStatus status, String transactionId) {
        return new PaymentResponse(5L, "PAY-TEST", 100L,
                PaymentMethod.CREDIT_CARD, "dummy-payment-service", status,
                new BigDecimal("500.00"), transactionId, null, null);
    }
}
