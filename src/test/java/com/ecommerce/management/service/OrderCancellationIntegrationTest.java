package com.ecommerce.management.service;

import com.ecommerce.management.entity.Category;
import com.ecommerce.management.entity.Customer;
import com.ecommerce.management.entity.Order;
import com.ecommerce.management.entity.OrderItem;
import com.ecommerce.management.entity.Product;
import com.ecommerce.management.entity.enums.OrderStatus;
import com.ecommerce.management.entity.enums.RecordStatus;
import com.ecommerce.management.repository.CategoryRepository;
import com.ecommerce.management.repository.CustomerRepository;
import com.ecommerce.management.repository.OrderItemRepository;
import com.ecommerce.management.repository.OrderRepository;
import com.ecommerce.management.repository.OrderStatusHistoryRepository;
import com.ecommerce.management.repository.OutboxEventRepository;
import com.ecommerce.management.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderCancellationIntegrationTest {

    @Autowired OrderService orderService;
    @Autowired CustomerRepository customerRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired EntityManager entityManager;

    @Test
    void shouldRestoreStockOnlyOnceAndPersistCancellationAtomically() {
        LocalDateTime now = LocalDateTime.now();
        Customer customer = customerRepository.save(customer(now));
        Category category = categoryRepository.save(category(now));
        Product product = productRepository.save(product(category, now));
        Order order = orderRepository.save(order(customer, now));
        orderItemRepository.save(orderItem(order, product, now));
        entityManager.flush();
        entityManager.clear();

        orderService.cancelOrder(order.getId());
        entityManager.flush();
        entityManager.clear();

        assertEquals(OrderStatus.CANCELLED,
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(7, productRepository.findById(product.getId()).orElseThrow().getStock());
        assertEquals(1, orderStatusHistoryRepository.count());
        assertEquals("order.cancelled", outboxEventRepository.findAll().getFirst().getEventType());

        orderService.cancelOrder(order.getId());
        entityManager.flush();
        entityManager.clear();

        assertEquals(7, productRepository.findById(product.getId()).orElseThrow().getStock());
        assertEquals(1, orderStatusHistoryRepository.count());
        assertEquals(1, outboxEventRepository.count());
    }

    private Customer customer(LocalDateTime now) {
        Customer customer = new Customer();
        customer.setName("Test Customer");
        customer.setEmail("cancel-test@example.com");
        customer.setStatus(RecordStatus.ACTIVE);
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        return customer;
    }

    private Category category(LocalDateTime now) {
        Category category = new Category();
        category.setName("Test Category");
        category.setSlug("cancel-test-category");
        category.setIsActive(true);
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        return category;
    }

    private Product product(Category category, LocalDateTime now) {
        Product product = new Product();
        product.setCategory(category);
        product.setName("Test Product");
        product.setSku("CANCEL-TEST-1");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(5);
        product.setStatus(RecordStatus.ACTIVE);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        return product;
    }

    private Order order(Customer customer, LocalDateTime now) {
        Order order = new Order();
        order.setOrderNo("ORD-CANCEL-TEST");
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PROCESSING);
        order.setCurrency("TRY");
        order.setSubtotal(new BigDecimal("200.00"));
        order.setDiscountTotal(BigDecimal.ZERO);
        order.setShippingTotal(BigDecimal.ZERO);
        order.setGrandTotal(new BigDecimal("200.00"));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        return order;
    }

    private OrderItem orderItem(Order order, Product product, LocalDateTime now) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setSku(product.getSku());
        item.setUnitPrice(product.getPrice());
        item.setQuantity(2);
        item.setDiscountTotal(BigDecimal.ZERO);
        item.setLineTotal(new BigDecimal("200.00"));
        item.setCreatedAt(now);
        return item;
    }
}
