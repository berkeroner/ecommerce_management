package com.ecommerce.management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.ecommerce.management.dto.order.OrderItemRequest;
import com.ecommerce.management.dto.order.OrderRequest;
import com.ecommerce.management.entity.Address;
import com.ecommerce.management.entity.Category;
import com.ecommerce.management.entity.Customer;
import com.ecommerce.management.entity.Product;
import com.ecommerce.management.entity.OutboxEvent;
import jakarta.persistence.EntityManager;
import com.ecommerce.management.entity.enums.PaymentMethod;
import com.ecommerce.management.entity.enums.AddressType;
import com.ecommerce.management.entity.enums.AddressableType;
import com.ecommerce.management.entity.enums.RecordStatus;
import com.ecommerce.management.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-rollback;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderServiceIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @MockitoSpyBean private OutboxEventRepository outboxEventRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearTestDatabase() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            outboxEventRepository.deleteAllInBatch();
            orderStatusHistoryRepository.deleteAllInBatch();
            addressRepository.deleteAllInBatch();
            orderItemRepository.deleteAllInBatch();
            orderRepository.deleteAllInBatch();
            productRepository.deleteAllInBatch();
            categoryRepository.deleteAllInBatch();
            customerRepository.deleteAllInBatch();
        });
    }

    @Test
    void shouldPersistOrderSnapshotsHistoryAndOutboxAndMergeDuplicateItems() {
        List<Long> ids = fixture(5);
        OrderRequest base = request(ids);
        OrderRequest duplicate = new OrderRequest(base.customerId(), base.paymentMethod(),
                base.shippingProvider(), base.shippingAddressId(), base.billingAddressId(),
                List.of(new OrderItemRequest(ids.get(1), 1),
                new OrderItemRequest(ids.get(1), 1)));
        var response = orderService.createOrder(duplicate);

        assertEquals(0, new BigDecimal("500.00").compareTo(response.totalAmount()));
        assertEquals(3, productRepository.findById(ids.get(1)).orElseThrow().getStock());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var order = orderRepository.findById(response.id()).orElseThrow();
            assertEquals(ids.get(0), order.getCustomer().getId());
            assertEquals(com.ecommerce.management.entity.enums.OrderStatus.PROCESSING, order.getStatus());
            assertEquals(1L, orderRepository.count());
            assertEquals(1L, orderItemRepository.count());
            var item = orderItemRepository.findAll().get(0);
            assertEquals(response.id(), item.getOrder().getId());
            assertEquals(2, item.getQuantity());
            assertEquals("Test Ürün SUCCESS", item.getProductName());
            assertEquals("SUCCESS", item.getSku());
            assertEquals(0, new BigDecimal("250.00").compareTo(item.getUnitPrice()));
            assertEquals(0, new BigDecimal("500.00").compareTo(item.getLineTotal()));
            assertEquals(3L, addressRepository.count());
            var addresses = addressRepository.findAllByAddressableTypeAndAddressableId(
                    AddressableType.ORDER, response.id());
            assertEquals(2, addresses.size());
            var address = addresses.stream()
                    .filter(candidate -> candidate.getAddressType() == AddressType.SHIPPING)
                    .findFirst().orElseThrow();
            assertEquals(response.id(), address.getAddressableId());
            assertEquals(com.ecommerce.management.entity.enums.AddressableType.ORDER, address.getAddressableType());
            assertEquals(com.ecommerce.management.entity.enums.AddressType.SHIPPING, address.getAddressType());
            assertEquals("Test Sokak No: 1", address.getAddressLine());
            assertEquals(1L, orderStatusHistoryRepository.count());
            var history = orderStatusHistoryRepository.findAll().get(0);
            assertEquals(response.id(), history.getOrder().getId());
            assertEquals(order.getStatus(), history.getNewStatus());
            assertEquals(1L, outboxEventRepository.count());
            var event = outboxEventRepository.findAll().get(0);
            assertEquals(response.id(), event.getAggregateId());
            assertEquals("order.created", event.getEventType());
            assertEquals(com.ecommerce.management.entity.enums.OutboxStatus.PENDING, event.getStatus());
            var payload = JsonMapper.builder().build().readTree(event.getPayload());
            assertEquals(event.getId().toString(), payload.path("event_id").asText());
            assertEquals("order.created", payload.path("event_type").asText());
            assertEquals(response.id().longValue(), payload.path("data").path("order_id").asLong());
            assertEquals("credit_card", payload.path("data").path("payment_method").asText());
            assertEquals("mock", payload.path("data").path("shipping_provider").asText());
        });
    }

    @Test
    void shouldNotOversellWhenTwoOrdersCompeteForRemainingStock() throws Exception {
        List<Long> ids = fixture(2);
        OrderRequest request = request(ids);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Integer> attempt = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test start timed out");
            }
            try {
                orderService.createOrder(request);
                return 201;
            } catch (ResponseStatusException exception) {
                return exception.getStatusCode().value();
            }
        };
        try {
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            org.junit.jupiter.api.Assertions.assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            var results = new java.util.ArrayList<>(List.of(
                    first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)));
            results.sort(Integer::compareTo);
            assertEquals(List.of(201, 409), results);
            assertEquals(0, productRepository.findById(ids.get(1)).orElseThrow().getStock());
            assertEquals(1L, orderRepository.count());
            assertEquals(1L, orderItemRepository.count());
            assertEquals(1L, outboxEventRepository.count());
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private List<Long> fixture(int stock) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            Customer customer = new Customer();
            customer.setName("Test Müşteri");
            customer.setEmail("success@example.com");
            customer.setStatus(RecordStatus.ACTIVE);
            customer.setCreatedAt(now);
            customer.setUpdatedAt(now);
            customerRepository.save(customer);
            Category category = new Category();
            category.setName("Elektronik");
            category.setSlug("success");
            category.setIsActive(true);
            category.setCreatedAt(now);
            category.setUpdatedAt(now);
            categoryRepository.save(category);
            Product product = productRepository.save(product(category, "SUCCESS", stock));
            Address address = addressRepository.save(address(customer.getId(), AddressType.SHIPPING));
            return List.of(customer.getId(), product.getId(), address.getId());
        });
    }

    private OrderRequest request(List<Long> ids) {
        return new OrderRequest(ids.get(0), PaymentMethod.CREDIT_CARD, "mock",
                ids.get(2), null, List.of(new OrderItemRequest(ids.get(1), 2)));
    }

    @Test
    void shouldRollbackFirstReservationWhenSecondProductHasInsufficientStock() {
        // Test verileri servis transaction'ından önce commit edilir.
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        List<Long> ids = transaction.execute(status -> {
            LocalDateTime now = LocalDateTime.now();

            Customer customer = new Customer();
            customer.setName("Test Müşteri");
            customer.setEmail("rollback@example.com");
            customer.setStatus(RecordStatus.ACTIVE);
            customer.setCreatedAt(now);
            customer.setUpdatedAt(now);
            customerRepository.save(customer);

            Category category = new Category();
            category.setName("Elektronik");
            category.setSlug("rollback-elektronik");
            category.setIsActive(true);
            category.setCreatedAt(now);
            category.setUpdatedAt(now);
            categoryRepository.save(category);

            Product first = productRepository.save(product(category, "FIRST", 5));
            Product second = productRepository.save(product(category, "SECOND", 1));
            Address address = addressRepository.save(address(customer.getId(), AddressType.SHIPPING));
            return List.of(customer.getId(), first.getId(), second.getId(), address.getId());
        });

        OrderRequest request = new OrderRequest(
                ids.get(0), PaymentMethod.CREDIT_CARD, "mock",
                ids.get(3), null,
                List.of(
                        new OrderItemRequest(ids.get(1), 2),
                        new OrderItemRequest(ids.get(2), 2)
                )
        );

        // Testte @Transactional yok: servis kendi transaction'ını açıp geri alır.
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(5, productRepository.findById(ids.get(1)).orElseThrow().getStock());
        assertEquals(1, productRepository.findById(ids.get(2)).orElseThrow().getStock());
        assertEquals(0L, orderRepository.count());
        assertEquals(0L, orderItemRepository.count());
        assertEquals(1L, addressRepository.count());
        assertEquals(0L, orderStatusHistoryRepository.count());
        assertEquals(0L, outboxEventRepository.count());
    }

    @Test
    void shouldRollbackSavedOrderAndStockWhenOutboxSaveFails() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        List<Long> ids = transaction.execute(status -> {
            LocalDateTime now = LocalDateTime.now();

            Customer customer = new Customer();
            customer.setName("Outbox Test Müşteri");
            customer.setEmail("outbox-rollback@example.com");
            customer.setStatus(RecordStatus.ACTIVE);
            customer.setCreatedAt(now);
            customer.setUpdatedAt(now);
            customerRepository.save(customer);

            Category category = new Category();
            category.setName("Outbox Test Kategori");
            category.setSlug("outbox-rollback");
            category.setIsActive(true);
            category.setCreatedAt(now);
            category.setUpdatedAt(now);
            categoryRepository.save(category);

            Product product = productRepository.save(product(category, "OUTBOX-ROLLBACK", 5));
            Address address = addressRepository.save(address(customer.getId(), AddressType.SHIPPING));
            return List.of(customer.getId(), product.getId(), address.getId());
        });

        OrderRequest request = new OrderRequest(
                ids.get(0), PaymentMethod.CREDIT_CARD, "mock",
                ids.get(2), null,
                List.of(new OrderItemRequest(ids.get(1), 2))
        );

        IllegalStateException failure = new IllegalStateException("Simulated outbox save failure");
        doAnswer(invocation -> {
            // Önceki kayıtları SQL'e gönderip hatanın gerçekten kayıt sonrasında olduğunu kanıtla.
            entityManager.flush();
            assertEquals(1L, orderRepository.count());
            assertEquals(1L, orderItemRepository.count());
            assertEquals(3L, addressRepository.count());
            assertEquals(1L, orderStatusHistoryRepository.count());
            int reservedStock = ((Number) entityManager.createNativeQuery(
                    "select stock from products where id = :id")
                    .setParameter("id", ids.get(1)).getSingleResult()).intValue();
            assertEquals(3, reservedStock);
            throw failure;
        }).when(outboxEventRepository).save(any(OutboxEvent.class));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> orderService.createOrder(request)
        );

        assertSame(failure, exception);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
        assertEquals(5, productRepository.findById(ids.get(1)).orElseThrow().getStock());
        assertEquals(0L, orderRepository.count());
        assertEquals(0L, orderItemRepository.count());
        assertEquals(1L, addressRepository.count());
        assertEquals(0L, orderStatusHistoryRepository.count());
        assertEquals(0L, outboxEventRepository.count());
    }

    private Product product(Category category, String sku, int stock) {
        LocalDateTime now = LocalDateTime.now();
        Product product = new Product();
        product.setCategory(category);
        product.setName("Test Ürün " + sku);
        product.setSku(sku);
        product.setPrice(new BigDecimal("250.00"));
        product.setStock(stock);
        product.setStatus(RecordStatus.ACTIVE);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        return product;
    }

    private Address address(Long customerId, AddressType type) {
        LocalDateTime now = LocalDateTime.now();
        Address address = new Address();
        address.setAddressableType(AddressableType.CUSTOMER);
        address.setAddressableId(customerId);
        address.setAddressType(type);
        address.setTitle("Ev");
        address.setCity("İstanbul");
        address.setDistrict("Kadıköy");
        address.setAddressLine("Test Sokak No: 1");
        address.setPostalCode("34710");
        address.setCreatedAt(now);
        address.setUpdatedAt(now);
        return address;
    }
}
