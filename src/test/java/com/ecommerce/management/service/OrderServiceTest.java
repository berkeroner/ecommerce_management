package com.ecommerce.management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.dto.order.OrderItemRequest;
import com.ecommerce.management.dto.order.OrderRequest;
import com.ecommerce.management.dto.order.OrderResponse;
import com.ecommerce.management.entity.Customer;
import com.ecommerce.management.entity.Product;
import com.ecommerce.management.entity.Address;
import com.ecommerce.management.entity.Order;
import com.ecommerce.management.entity.OrderItem;
import com.ecommerce.management.entity.OrderStatusHistory;
import com.ecommerce.management.entity.OutboxEvent;
import com.ecommerce.management.entity.enums.OrderStatus;
import com.ecommerce.management.entity.enums.AddressType;
import com.ecommerce.management.entity.enums.AddressableType;
import com.ecommerce.management.entity.enums.PaymentMethod;
import com.ecommerce.management.entity.enums.PaymentStatus;
import com.ecommerce.management.entity.enums.RecordStatus;
import com.ecommerce.management.repository.AddressRepository;
import com.ecommerce.management.repository.CustomerRepository;
import com.ecommerce.management.repository.OrderItemRepository;
import com.ecommerce.management.repository.OrderRepository;
import com.ecommerce.management.repository.OrderStatusHistoryRepository;
import com.ecommerce.management.repository.OutboxEventRepository;
import com.ecommerce.management.repository.PaymentRepository;
import com.ecommerce.management.repository.ProductRepository;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AddressRepository addressRepository;

    @Spy
    private JsonMapper objectMapper = JsonMapper.builder().build();

    @InjectMocks
    private OrderService orderService;

    @BeforeEach
    void setUpCustomerAddresses() {
        lenient().when(addressRepository.findByIdAndAddressableTypeAndAddressableId(
                        20L, AddressableType.CUSTOMER, 1L))
                .thenReturn(Optional.of(address(20L, AddressType.SHIPPING)));
        lenient().when(addressRepository.findByIdAndAddressableTypeAndAddressableId(
                        21L, AddressableType.CUSTOMER, 1L))
                .thenReturn(Optional.of(address(21L, AddressType.BILLING)));
    }

    @Test
    void shouldCreateOrderSuccessfully() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setStatus(RecordStatus.ACTIVE);

        Product product = new Product();
        product.setId(10L);
        product.setName("Klavye");
        product.setSku("KEYBOARD-001");
        product.setPrice(new BigDecimal("250.00"));
        product.setStock(5);
        product.setStatus(RecordStatus.ACTIVE);

        OrderRequest request = new OrderRequest(
                1L,
                PaymentMethod.CREDIT_CARD,
                "mock",
                20L,
                21L,
                List.of(new OrderItemRequest(10L, 2))
        );

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));
        when(productRepository.findById(10L))
                .thenReturn(Optional.of(product));
        when(productRepository.reserveStock(
                eq(10L), eq(2), eq(RecordStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(1);
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    order.setId(100L);
                    return order;
                });

        OrderResponse response = orderService.createOrder(request);

        assertEquals(100L, response.id());
        assertEquals(OrderStatus.PROCESSING, response.status());
        assertEquals("TRY", response.currency());
        assertEquals(0, new BigDecimal("500.00").compareTo(response.totalAmount()));

        verify(orderItemRepository).saveAll(argThat(items -> {
            var iterator = items.iterator();
            if (!iterator.hasNext()) {
                return false;
            }
            OrderItem item = iterator.next();
            return !iterator.hasNext()
                    && item.getQuantity() == 2
                    && item.getProductName().equals("Klavye")
                    && item.getSku().equals("KEYBOARD-001")
                    && item.getUnitPrice().compareTo(new BigDecimal("250.00")) == 0
                    && item.getLineTotal().compareTo(new BigDecimal("500.00")) == 0
                    && item.getOrder().getId().equals(100L);
        }));

        verify(addressRepository, times(2)).save(any(Address.class));
        verify(orderStatusHistoryRepository).save(any(OrderStatusHistory.class));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void shouldRejectMissingCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> orderService.createOrder(request(2))).getStatusCode());
        verifyNoInteractions(productRepository, orderRepository, outboxEventRepository);
    }

    @Test
    void shouldRejectPassiveCustomer() {
        Customer customer = activeCustomer();
        customer.setStatus(RecordStatus.PASSIVE);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> orderService.createOrder(request(2))).getStatusCode());
        verifyNoInteractions(productRepository, orderRepository, outboxEventRepository);
    }

    @Test
    void shouldRejectAddressThatDoesNotBelongToCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(activeCustomer()));
        when(addressRepository.findByIdAndAddressableTypeAndAddressableId(
                20L, AddressableType.CUSTOMER, 1L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(request(2)));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(productRepository, orderRepository, outboxEventRepository);
    }

    @Test
    void shouldRejectExplicitBillingAddressWithWrongType() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(activeCustomer()));
        when(addressRepository.findByIdAndAddressableTypeAndAddressableId(
                21L, AddressableType.CUSTOMER, 1L))
                .thenReturn(Optional.of(address(21L, AddressType.SHIPPING)));
        OrderRequest request = new OrderRequest(1L, PaymentMethod.CREDIT_CARD, "mock",
                20L, 21L, List.of(new OrderItemRequest(10L, 2)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(request));

        assertEquals(422, exception.getStatusCode().value());
        verifyNoInteractions(productRepository, orderRepository, outboxEventRepository);
    }

    @Test
    void shouldRejectMissingProduct() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(activeCustomer()));
        when(productRepository.findById(10L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> orderService.createOrder(request(2))).getStatusCode());
        verify(productRepository, never()).reserveStock(any(), anyInt(), any(), any());
        verifyNoInteractions(orderRepository, outboxEventRepository);
    }

    @Test
    void shouldRejectPassiveProduct() {
        Product product = new Product();
        product.setStatus(RecordStatus.PASSIVE);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(activeCustomer()));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> orderService.createOrder(request(2))).getStatusCode());
        verify(productRepository, never()).reserveStock(any(), anyInt(), any(), any());
        verifyNoInteractions(orderRepository, outboxEventRepository);
    }

    @Test
    void shouldRejectQuantityOverflowBeforeReservation() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(activeCustomer()));
        OrderRequest base = request(1);
        OrderRequest overflow = new OrderRequest(base.customerId(), base.paymentMethod(),
                base.shippingProvider(), base.shippingAddressId(), base.billingAddressId(),
                List.of(new OrderItemRequest(10L, Integer.MAX_VALUE),
                new OrderItemRequest(10L, 1)));
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> orderService.createOrder(overflow)).getStatusCode().value());
        verifyNoInteractions(productRepository, orderRepository, outboxEventRepository);
    }

    @Test
    void shouldReturnDetailAndStatus() {
        Order order = new Order();
        order.setId(100L);
        order.setOrderNo("ORD-TEST");
        order.setStatus(OrderStatus.PROCESSING);
        order.setGrandTotal(new BigDecimal("500.00"));
        order.setCurrency("TRY");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        OrderResponse detail = orderService.findById(100L);
        assertEquals(100L, detail.id());
        assertEquals("ORD-TEST", detail.orderNo());
        assertEquals(order.getGrandTotal(), detail.totalAmount());
        assertEquals("TRY", detail.currency());
        assertEquals(OrderStatus.PROCESSING, detail.status());
        assertEquals(100L, orderService.getStatus(100L).id());
        assertEquals(OrderStatus.PROCESSING, orderService.getStatus(100L).status());
    }

    @Test
    void shouldRejectMissingOrderForDetailAndStatus() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> orderService.findById(999L)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> orderService.getStatus(999L)).getStatusCode());
    }

    @Test
    void shouldCancelOrderRestoreStockAndWriteTransactionalRecords() {
        Order order = order(OrderStatus.PROCESSING);
        Product product = new Product();
        product.setId(10L);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);

        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(100L)).thenReturn(List.of(item));
        when(productRepository.releaseStock(eq(10L), eq(2), any(LocalDateTime.class)))
                .thenReturn(1);

        OrderResponse response = orderService.cancelOrder(100L);

        assertEquals(OrderStatus.CANCELLED, response.status());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderRepository).save(order);
        verify(orderStatusHistoryRepository).save(argThat(history ->
                history.getPreviousStatus() == OrderStatus.PROCESSING
                        && history.getNewStatus() == OrderStatus.CANCELLED));
        verify(outboxEventRepository).save(argThat(event ->
                event.getEventType().equals("order.cancelled")
                        && event.getAggregateId().equals(100L)));
    }

    @Test
    void shouldReturnCancelledOrderWithoutRestoringStockAgain() {
        Order order = order(OrderStatus.CANCELLED);
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.cancelOrder(100L);

        assertEquals(OrderStatus.CANCELLED, response.status());
        verifyNoInteractions(orderItemRepository, productRepository);
        verify(orderStatusHistoryRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldRejectFailedOrderCancellation() {
        when(orderRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(order(OrderStatus.FAILED)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(100L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(orderItemRepository, productRepository);
    }

    @Test
    void shouldRequireRefundBeforeCancellingPaidOrder() {
        when(orderRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(order(OrderStatus.CONFIRMED)));
        when(paymentRepository.existsByOrderIdAndStatusIn(
                eq(100L), eq(java.util.EnumSet.of(PaymentStatus.COMPLETED))))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(100L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(orderItemRepository, productRepository);
    }

    @Test
    void shouldReturnNotFoundWhenCancelledOrderIsMissing() {
        when(orderRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(999L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void shouldStopCancellationWhenStockCannotBeRestored() {
        Order order = order(OrderStatus.CONFIRMED);
        Product product = new Product();
        product.setId(10L);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);
        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(100L)).thenReturn(List.of(item));
        when(productRepository.releaseStock(eq(10L), eq(2), any(LocalDateTime.class)))
                .thenReturn(0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(100L)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        verify(orderRepository, never()).save(any());
        verify(orderStatusHistoryRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    private Customer activeCustomer() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setStatus(RecordStatus.ACTIVE);
        return customer;
    }

    private Order order(OrderStatus status) {
        Customer customer = new Customer();
        customer.setId(1L);
        Order order = new Order();
        order.setId(100L);
        order.setOrderNo("ORD-TEST");
        order.setCustomer(customer);
        order.setStatus(status);
        order.setGrandTotal(new BigDecimal("500.00"));
        order.setCurrency("TRY");
        return order;
    }

    private OrderRequest request(int quantity) {
        return new OrderRequest(1L, PaymentMethod.CREDIT_CARD, "mock",
                20L, null, List.of(new OrderItemRequest(10L, quantity)));
    }

    private Address address(Long id, AddressType type) {
        Address address = new Address();
        address.setId(id);
        address.setAddressableType(AddressableType.CUSTOMER);
        address.setAddressableId(1L);
        address.setAddressType(type);
        address.setTitle("Ev");
        address.setCity("İstanbul");
        address.setDistrict("Kadıköy");
        address.setAddressLine("Test Sokak No: 1");
        address.setPostalCode("34710");
        return address;
    }

    @Test
    void shouldRejectOrderWhenStockIsInsufficient() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setStatus(RecordStatus.ACTIVE);

        Product product = new Product();
        product.setId(10L);
        product.setName("Klavye");
        product.setSku("KEYBOARD-001");
        product.setPrice(new BigDecimal("250.00"));
        product.setStock(1);
        product.setStatus(RecordStatus.ACTIVE);

        OrderRequest request = new OrderRequest(
                1L,
                PaymentMethod.CREDIT_CARD,
                "mock",
                20L,
                null,
                List.of(new OrderItemRequest(10L, 2))
        );

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productRepository.reserveStock(
                eq(10L), eq(2), eq(RecordStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(productRepository).reserveStock(
                eq(10L), eq(2), eq(RecordStatus.ACTIVE), any(LocalDateTime.class));
        verifyNoInteractions(
                orderRepository,
                orderItemRepository,
                orderStatusHistoryRepository,
                outboxEventRepository
        );
        verify(addressRepository, never()).save(any());
    }
}
