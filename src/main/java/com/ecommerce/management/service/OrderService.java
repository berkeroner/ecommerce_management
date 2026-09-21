package com.ecommerce.management.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.dto.order.OrderItemRequest;
import com.ecommerce.management.dto.order.OrderRequest;
import com.ecommerce.management.dto.order.OrderResponse;
import com.ecommerce.management.dto.order.OrderStatusResponse;
import com.ecommerce.management.dto.order.OrderShippingAddressRequest;
import com.ecommerce.management.entity.Address;
import com.ecommerce.management.entity.Customer;
import com.ecommerce.management.entity.Order;
import com.ecommerce.management.entity.OrderItem;
import com.ecommerce.management.entity.OrderStatusHistory;
import com.ecommerce.management.entity.OutboxEvent;
import com.ecommerce.management.entity.Product;
import com.ecommerce.management.entity.enums.AddressType;
import com.ecommerce.management.entity.enums.AddressableType;
import com.ecommerce.management.entity.enums.OrderStatus;
import com.ecommerce.management.entity.enums.OutboxStatus;
import com.ecommerce.management.entity.enums.RecordStatus;
import com.ecommerce.management.repository.AddressRepository;
import com.ecommerce.management.repository.CustomerRepository;
import com.ecommerce.management.repository.OrderItemRepository;
import com.ecommerce.management.repository.OrderRepository;
import com.ecommerce.management.repository.OrderStatusHistoryRepository;
import com.ecommerce.management.repository.OutboxEventRepository;
import com.ecommerce.management.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;



@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OutboxEventRepository outboxEventRepository;

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        Customer customer = customerRepository
                .findById(request.customerId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found: " + request.customerId()
                ));

        if (customer.getStatus() != RecordStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Customer is not active"
            );
        }

        Map<Long, Integer> quantities = new TreeMap<>();

        for (OrderItemRequest item : request.items()) {
            try {
                quantities.merge(
                        item.productId(),
                        item.quantity(),
                        Math::addExact
                );
            } catch (ArithmeticException ex) {
                throw new ResponseStatusException(
                        HttpStatus.valueOf(422),
                        "Total quantity is too large"
                );
            }
        }

        LocalDateTime now = LocalDateTime.now();
        var orderItems = new ArrayList<OrderItem>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (var entry : quantities.entrySet()) {
            Long productId = entry.getKey();
            int quantity = entry.getValue();

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Product not found: " + productId
                    ));

            if (product.getStatus() != RecordStatus.ACTIVE) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Product is not active: " + productId
                );
            }

            int updated = productRepository.reserveStock(
                    productId,
                    quantity,
                    RecordStatus.ACTIVE,
                    now
            );

            if (updated == 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Insufficient stock or product is inactive: "
                                + productId
                );
            }

            BigDecimal lineTotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(quantity));

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setProductName(product.getName());
            orderItem.setSku(product.getSku());
            orderItem.setUnitPrice(product.getPrice());
            orderItem.setQuantity(quantity);
            orderItem.setDiscountTotal(BigDecimal.ZERO);
            orderItem.setLineTotal(lineTotal);
            orderItem.setCreatedAt(now);

            orderItems.add(orderItem);
            subtotal = subtotal.add(lineTotal);
        }

        if (subtotal.compareTo(
                new BigDecimal("9999999999.99")) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.valueOf(422),
                    "Order total is too large"
            );
        }

        Order order = new Order();
        order.setOrderNo("ORD-" + UUID.randomUUID());
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PROCESSING);
        order.setCurrency("TRY");
        order.setSubtotal(subtotal);
        order.setDiscountTotal(BigDecimal.ZERO);
        order.setShippingTotal(BigDecimal.ZERO);
        order.setGrandTotal(subtotal);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        order = orderRepository.save(order);

        for (OrderItem item : orderItems) {
            item.setOrder(order);
        }

        orderItemRepository.saveAll(orderItems);

        saveShippingAddress(order, request.shippingAddress(), now);
        saveInitialHistory(order, now);
        saveCreatedEvent(order, request, now);

        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                order.getGrandTotal(),
                order.getCurrency()
        );
    }

    @Transactional (readOnly = true)
    public OrderResponse findById(Long id) {
        return toResponse(getOrder(id));
    }

    private Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found: " + id));
    }

    @Transactional (readOnly = true)
    public OrderStatusResponse getStatus(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found: " + id));

        return new OrderStatusResponse(order.getId(), order.getStatus());
    }

    private void saveShippingAddress(
            Order order,
            OrderShippingAddressRequest request,
            LocalDateTime now
    ) {
        Address address = new Address();
        address.setAddressableType(AddressableType.ORDER);
        address.setAddressableId(order.getId());
        address.setAddressType(AddressType.SHIPPING);
        address.setTitle(request.title().trim());
        address.setCity(request.city().trim());
        address.setDistrict(request.district().trim());
        address.setAddressLine(request.addressLine().trim());
        address.setPostalCode(
                request.postalCode() == null
                        ? null
                        : request.postalCode().trim()
        );
        address.setCreatedAt(now);
        address.setUpdatedAt(now);

        addressRepository.save(address);
    }

    private void saveInitialHistory(
            Order order,
            LocalDateTime now
    ) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setPreviousStatus(null);
        history.setNewStatus(order.getStatus());
        history.setReason("Order created and stock reserved");
        history.setCreatedAt(now);

        orderStatusHistoryRepository.save(history);
    }

    private void saveCreatedEvent(
            Order order,
            OrderRequest request,
            LocalDateTime now
    ) {
        UUID eventId = UUID.randomUUID();

        String correlationId = MDC.get("correlation_id");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        var data = Map.of(
                "order_id", order.getId(),
                "customer_id", order.getCustomer().getId(),
                "grand_total", order.getGrandTotal(),
                "currency", order.getCurrency(),
                "payment_method", request.paymentMethod(),
                "shipping_provider", request.shippingProvider().trim()
        );

        var envelope = Map.of(
                "event_id", eventId.toString(),
                "event_type", "order.created",
                "occurred_at", Instant.now().toString(),
                "correlation_id", correlationId,
                "data", data
        );

        OutboxEvent event = new OutboxEvent();
        event.setId(eventId);
        event.setAggregateType("order");
        event.setAggregateId(order.getId());
        event.setEventType("order.created");
        event.setPayload(objectMapper.writeValueAsString(envelope));
        event.setStatus(OutboxStatus.PENDING);
        event.setRetryCount(0);
        event.setCreatedAt(now);

        outboxEventRepository.save(event);
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                order.getGrandTotal(),
                order.getCurrency()
        );
    }
}