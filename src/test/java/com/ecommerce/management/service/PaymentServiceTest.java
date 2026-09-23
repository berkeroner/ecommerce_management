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
import com.ecommerce.management.dto.payment.provider.PaymentCallbackRequest;
import com.ecommerce.management.dto.payment.provider.ProviderPaymentStatus;
import com.ecommerce.management.entity.Customer;
import com.ecommerce.management.entity.Order;
import com.ecommerce.management.entity.OutboxEvent;
import com.ecommerce.management.entity.Payment;
import com.ecommerce.management.entity.OrderItem;
import com.ecommerce.management.entity.Product;
import com.ecommerce.management.entity.enums.OrderStatus;
import com.ecommerce.management.entity.enums.PaymentMethod;
import com.ecommerce.management.entity.enums.PaymentStatus;
import com.ecommerce.management.payment.PaymentStrategy;
import com.ecommerce.management.payment.PaymentStrategyFactory;
import com.ecommerce.management.repository.OrderRepository;
import com.ecommerce.management.repository.OrderItemRepository;
import com.ecommerce.management.repository.OrderStatusHistoryRepository;
import com.ecommerce.management.repository.OutboxEventRepository;
import com.ecommerce.management.repository.PaymentRepository;
import com.ecommerce.management.repository.ProductRepository;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private PaymentStrategyFactory strategyFactory;
    @Mock private PaymentStrategy strategy;
    @Mock private DummyPaymentClient dummyPaymentClient;
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
    void shouldStartPaymentAsProcessing() {
        mockOrderLookup();
        mockProvider();

        PaymentResponse response = paymentService.startPayment(
                100L, new PaymentRequest(PaymentMethod.CREDIT_CARD, "token"));

        assertEquals(PaymentStatus.PROCESSING, response.status());
        assertEquals(OrderStatus.PROCESSING, order.getStatus());
        assertEquals("11111111-1111-1111-1111-111111111111", response.transactionId());
        verify(outboxEventRepository).save(argThat(event ->
                event.getEventType().equals("payment.requested")));
        verify(dummyPaymentClient).createPayment(argThat(providerRequest ->
                providerRequest.orderId().equals("100")
                        && providerRequest.items().size() == 1
                        && providerRequest.items().getFirst().productName().equals("Test product")
                        && providerRequest.items().getFirst().quantity() == 2
                        && providerRequest.items().getFirst().unitPrice()
                                .compareTo(new BigDecimal("250.00")) == 0));
    }

    @Test
    void shouldRejectPaymentFromCallbackAndFailOrder() {
        UUID providerPaymentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Payment payment = payment(PaymentStatus.PROCESSING);
        payment.setTransactionId(providerPaymentId.toString());
        when(paymentRepository.findByTransactionIdForUpdate(providerPaymentId.toString()))
                .thenReturn(Optional.of(payment));
        Product product = new Product();
        product.setId(10L);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);
        when(orderItemRepository.findAllByOrderId(100L)).thenReturn(java.util.List.of(item));
        when(productRepository.releaseStock(any(), any(Integer.class), any())).thenReturn(1);

        PaymentResponse response = paymentService.handleCallback(new PaymentCallbackRequest(
                providerPaymentId,
                "100",
                ProviderPaymentStatus.REJECTED,
                new BigDecimal("500.00"),
                "TRY",
                "declined"
        ));

        assertEquals(PaymentStatus.FAILED, response.status());
        assertEquals(OrderStatus.FAILED, order.getStatus());
        verify(productRepository).releaseStock(any(), any(Integer.class), any());
        verify(outboxEventRepository).save(argThat(event ->
                event.getEventType().equals("payment.failed")));
    }

    @Test
    void shouldRejectDuplicatePayment() {
        mockOrderLookup();
        when(paymentRepository.existsByOrderIdAndStatusIn(any(), any())).thenReturn(true);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> paymentService.startPayment(100L,
                        new PaymentRequest(PaymentMethod.CREDIT_CARD, "token")));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(strategyFactory, never()).get(any());
        verify(dummyPaymentClient, never()).createPayment(any());
    }

    @Test
    void shouldRefundCompletedPaymentAndBeIdempotent() {
        Payment payment = payment(PaymentStatus.COMPLETED);
        when(paymentRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(payment));
        when(strategyFactory.get(PaymentMethod.CREDIT_CARD)).thenReturn(strategy);
        when(strategy.refund(payment)).thenReturn("REF-1");

        assertEquals(PaymentStatus.REFUNDED, paymentService.refund(5L).status());
        assertEquals(PaymentStatus.REFUNDED, paymentService.refund(5L).status());
        verify(strategy).refund(payment);
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

    private void mockProvider() {
        OrderItem item = new OrderItem();
        item.setProductName("Test product");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("250.00"));
        when(orderItemRepository.findAllByOrderId(100L)).thenReturn(java.util.List.of(item));
        when(dummyPaymentClient.createPayment(any())).thenReturn(new DummyPaymentAcceptedResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "100",
                ProviderPaymentStatus.PROCESSING
        ));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(5L);
            return payment;
        });
    }

    private void mockOrderLookup() {
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order));
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
}
