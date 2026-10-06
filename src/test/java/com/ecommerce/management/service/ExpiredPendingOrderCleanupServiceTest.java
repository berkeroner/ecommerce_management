package com.ecommerce.management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.ecommerce.management.entity.Category;
import com.ecommerce.management.entity.Customer;
import com.ecommerce.management.entity.Order;
import com.ecommerce.management.entity.OrderItem;
import com.ecommerce.management.entity.OrderStatusHistory;
import com.ecommerce.management.entity.Payment;
import com.ecommerce.management.entity.Product;
import com.ecommerce.management.entity.enums.OrderStatus;
import com.ecommerce.management.entity.enums.PaymentMethod;
import com.ecommerce.management.entity.enums.PaymentStatus;
import com.ecommerce.management.entity.enums.RecordStatus;
import com.ecommerce.management.repository.CategoryRepository;
import com.ecommerce.management.repository.CustomerRepository;
import com.ecommerce.management.repository.OrderItemRepository;
import com.ecommerce.management.repository.OrderRepository;
import com.ecommerce.management.repository.OrderStatusHistoryRepository;
import com.ecommerce.management.repository.PaymentRepository;
import com.ecommerce.management.repository.ProductRepository;

import jakarta.persistence.EntityManager;

@DataJpaTest
@Import(ExpiredPendingOrderCleanupService.class)
@TestPropertySource(properties = "order.pending-payment-timeout=10m")
class ExpiredPendingOrderCleanupServiceTest {

    @Autowired ExpiredPendingOrderCleanupService cleanupService;
    @Autowired CustomerRepository customerRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired EntityManager entityManager;

    @Test
    void deletesOnlyExpiredPendingOrdersAndRestoresTheirStock() {
        LocalDateTime now = LocalDateTime.now();
        Customer customer = customerRepository.save(customer(now));
        Category category = categoryRepository.save(category(now));
        Product product = productRepository.save(product(category, now));

        Order expiredPending = orderRepository.save(order(
                customer, "ORD-EXPIRED", now.minusMinutes(11)));
        orderItemRepository.save(orderItem(expiredPending, product, now.minusMinutes(11)));
        orderStatusHistoryRepository.save(history(expiredPending, now.minusMinutes(11)));
        paymentRepository.save(payment(expiredPending, PaymentStatus.PENDING, now.minusMinutes(11)));

        Order recentPending = orderRepository.save(order(
                customer, "ORD-RECENT", now.minusMinutes(9)));
        paymentRepository.save(payment(recentPending, PaymentStatus.PENDING, now.minusMinutes(9)));

        Order completed = orderRepository.save(order(
                customer, "ORD-COMPLETED", now.minusMinutes(20)));
        paymentRepository.save(payment(completed, PaymentStatus.COMPLETED, now.minusMinutes(20)));

        entityManager.flush();
        entityManager.clear();

        var result = cleanupService.cleanup();

        assertEquals(1, result.deletedOrderCount());
        assertFalse(orderRepository.existsById(expiredPending.getId()));
        assertTrue(orderRepository.existsById(recentPending.getId()));
        assertTrue(orderRepository.existsById(completed.getId()));
        assertEquals(5, productRepository.findById(product.getId()).orElseThrow().getStock());
        assertEquals(2, paymentRepository.count());
        assertEquals(0, orderStatusHistoryRepository.count());
    }

    private Customer customer(LocalDateTime now) {
        Customer customer = new Customer();
        customer.setName("Cleanup Customer");
        customer.setEmail("cleanup@example.com");
        customer.setStatus(RecordStatus.ACTIVE);
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        return customer;
    }

    private Category category(LocalDateTime now) {
        Category category = new Category();
        category.setName("Cleanup Category");
        category.setSlug("cleanup-category");
        category.setIsActive(true);
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        return category;
    }

    private Product product(Category category, LocalDateTime now) {
        Product product = new Product();
        product.setCategory(category);
        product.setName("Cleanup Product");
        product.setSku("CLEANUP-1");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(3);
        product.setStatus(RecordStatus.ACTIVE);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        return product;
    }

    private Order order(Customer customer, String orderNo, LocalDateTime createdAt) {
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PROCESSING);
        order.setCurrency("TRY");
        order.setSubtotal(new BigDecimal("200.00"));
        order.setDiscountTotal(BigDecimal.ZERO);
        order.setShippingTotal(BigDecimal.ZERO);
        order.setGrandTotal(new BigDecimal("200.00"));
        order.setCreatedAt(createdAt);
        order.setUpdatedAt(createdAt);
        return order;
    }

    private OrderItem orderItem(Order order, Product product, LocalDateTime createdAt) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setSku(product.getSku());
        item.setUnitPrice(product.getPrice());
        item.setQuantity(2);
        item.setDiscountTotal(BigDecimal.ZERO);
        item.setLineTotal(new BigDecimal("200.00"));
        item.setCreatedAt(createdAt);
        return item;
    }

    private OrderStatusHistory history(Order order, LocalDateTime createdAt) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setNewStatus(OrderStatus.PROCESSING);
        history.setReason("Order created");
        history.setCreatedAt(createdAt);
        return history;
    }

    private Payment payment(Order order, PaymentStatus status, LocalDateTime createdAt) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentNo("PAY-" + UUID.randomUUID());
        payment.setMethod(PaymentMethod.CREDIT_CARD);
        payment.setProvider("dummy-payment-service");
        payment.setStatus(status);
        payment.setAmount(order.getGrandTotal());
        payment.setIdempotencyKey(UUID.randomUUID().toString());
        payment.setCreatedAt(createdAt);
        payment.setUpdatedAt(createdAt);
        return payment;
    }
}
