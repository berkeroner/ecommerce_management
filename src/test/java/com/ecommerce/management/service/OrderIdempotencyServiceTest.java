package com.ecommerce.management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.dto.order.OrderItemRequest;
import com.ecommerce.management.dto.order.OrderRequest;
import com.ecommerce.management.dto.order.OrderResponse;
import com.ecommerce.management.entity.enums.OrderStatus;
import com.ecommerce.management.entity.enums.PaymentMethod;

import tools.jackson.databind.json.JsonMapper;

class OrderIdempotencyServiceTest {

    private final OrderService orderService = mock(OrderService.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final AtomicReference<String> storedValue = new AtomicReference<>();

    private OrderIdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new OrderIdempotencyService(orderService, redisTemplate,
                JsonMapper.builder().build(), Duration.ofMinutes(5), Duration.ofHours(24));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenAnswer(invocation -> {
            String value = invocation.getArgument(1);
            return storedValue.compareAndSet(null, value);
        });
        when(valueOperations.get(any())).thenAnswer(invocation -> storedValue.get());
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    Object[] arguments = invocation.getArguments();
                    if (arguments.length == 5
                            && storedValue.compareAndSet((String) arguments[2], (String) arguments[3])) {
                        return 1L;
                    }
                    if (arguments.length == 3
                            && storedValue.compareAndSet((String) arguments[2], null)) {
                        return 1L;
                    }
                    return 0L;
                });
    }

    @Test
    void shouldCreateOnceAndReplayCompletedResponse() {
        OrderResponse response = response();
        when(orderService.createOrder(request(2))).thenReturn(response);

        var first = service.createOrder("checkout-123", request(2));
        var replay = service.createOrder("checkout-123", request(2));

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(response, replay.response());
        verify(orderService, times(1)).createOrder(request(2));
    }

    @Test
    void shouldRejectReusingKeyWithDifferentPayload() {
        when(orderService.createOrder(request(2))).thenReturn(response());
        service.createOrder("checkout-123", request(2));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createOrder("checkout-123", request(1)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Idempotency-Key has already been used with a different request",
                exception.getReason());
        verify(orderService, times(1)).createOrder(any());
    }

    @Test
    void shouldRejectConcurrentRequestWhileFirstRequestIsProcessing() {
        when(orderService.createOrder(request(2))).thenAnswer(invocation -> {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> service.createOrder("checkout-123", request(2)));
            assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
            assertEquals("A request with this Idempotency-Key is already being processed",
                    exception.getReason());
            return response();
        });

        service.createOrder("checkout-123", request(2));

        verify(orderService, times(1)).createOrder(request(2));
    }

    @Test
    void shouldReleaseKeyAfterFailedCreationSoRequestCanBeRetried() {
        when(orderService.createOrder(request(2)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock"))
                .thenReturn(response());

        assertThrows(ResponseStatusException.class,
                () -> service.createOrder("checkout-123", request(2)));
        var retried = service.createOrder("checkout-123", request(2));

        assertFalse(retried.replayed());
        assertEquals(response(), retried.response());
        verify(orderService, times(2)).createOrder(request(2));
    }

    @Test
    void shouldRejectBlankAndOversizedKeys() {
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class,
                        () -> service.createOrder(" ", request(2))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class,
                        () -> service.createOrder("x".repeat(129), request(2))).getStatusCode());
    }

    @Test
    void shouldFailClosedWhenRedisIsUnavailable() {
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("Redis is down"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createOrder("checkout-123", request(2)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        verify(orderService, times(0)).createOrder(any());
    }

    private OrderRequest request(int quantity) {
        return new OrderRequest(1L, PaymentMethod.CREDIT_CARD, "mock", 20L, 21L,
                List.of(new OrderItemRequest(10L, quantity)));
    }

    private OrderResponse response() {
        return new OrderResponse(100L, "ORD-TEST", OrderStatus.PROCESSING,
                new BigDecimal("500.00"), "TRY");
    }
}
