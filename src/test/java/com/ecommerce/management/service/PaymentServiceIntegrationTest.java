package com.ecommerce.management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.management.client.DummyPaymentClient;
import com.ecommerce.management.dto.payment.PaymentRequest;
import com.ecommerce.management.dto.payment.PaymentResponse;
import com.ecommerce.management.dto.payment.provider.DummyPaymentAcceptedResponse;
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

import jakarta.persistence.EntityManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentServiceIntegrationTest {

    @Autowired PaymentService paymentService;
    @Autowired CustomerRepository customerRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired OrderStatusHistoryRepository historyRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired EntityManager entityManager;
    @MockitoBean DummyPaymentClient dummyPaymentClient;
    @MockitoBean OrderItemRepository orderItemRepository;

    @Test
    void shouldCompleteAndRefundCardPaymentWithTransactionalRecords() {
        LocalDateTime now = LocalDateTime.now();
        Customer customer = customerRepository.save(customer(now));
        Order order = orderRepository.save(order(customer, now));
        entityManager.flush();

        UUID providerPaymentId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        OrderItem item = new OrderItem();
        item.setProductName("Integration product");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("250.00"));
        when(orderItemRepository.findAllByOrderId(order.getId())).thenReturn(List.of(item));
        when(dummyPaymentClient.createPayment(any())).thenReturn(
                new DummyPaymentAcceptedResponse(
                        providerPaymentId,
                        order.getId().toString(),
                        ProviderPaymentStatus.PROCESSING
                ));

        PaymentResponse processing = paymentService.startPayment(order.getId(),
                new PaymentRequest(PaymentMethod.CREDIT_CARD, "secret-token"));
        entityManager.flush();
        entityManager.clear();

        assertEquals(PaymentStatus.PROCESSING, processing.status());
        assertEquals(OrderStatus.PROCESSING,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());

        PaymentResponse completed = paymentService.handleCallback(new PaymentCallbackRequest(
                providerPaymentId,
                order.getId().toString(),
                ProviderPaymentStatus.APPROVED,
                new BigDecimal("500.00"),
                "TRY",
                "Payment approved"
        ));
        entityManager.flush();
        entityManager.clear();

        assertEquals(PaymentStatus.COMPLETED, completed.status());
        assertEquals(OrderStatus.CONFIRMED,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(1, paymentRepository.count());
        assertEquals(1, historyRepository.count());
        assertEquals(3, outboxEventRepository.count());
        assertFalse(outboxEventRepository.findAll().stream()
                .anyMatch(event -> event.getPayload().contains("secret-token")));

        PaymentResponse refunded = paymentService.refund(completed.id());
        entityManager.flush();

        assertEquals(PaymentStatus.REFUNDED, refunded.status());
        assertEquals(4, outboxEventRepository.count());
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
