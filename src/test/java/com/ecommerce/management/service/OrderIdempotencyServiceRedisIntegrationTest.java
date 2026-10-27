package com.ecommerce.management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.ecommerce.management.dto.order.OrderItemRequest;
import com.ecommerce.management.dto.order.OrderRequest;
import com.ecommerce.management.dto.order.OrderResponse;
import com.ecommerce.management.entity.enums.OrderStatus;

import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class OrderIdempotencyServiceRedisIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static final Duration DEFAULT_PROCESSING_TTL = Duration.ofSeconds(5);
    private static final Duration DEFAULT_COMPLETED_TTL = Duration.ofSeconds(5);

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(REDIS_PORT);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private ExecutorService executor;

    @BeforeAll
    static void connectToRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(),
                REDIS.getMappedPort(REDIS_PORT));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void disconnectFromRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        flushRedis();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void shouldCreateOnceAndReplayCompletedResponseFromRedis() {
        OrderService orderService = mock(OrderService.class);
        OrderResponse response = response(100L);
        when(orderService.createOrder(request(2))).thenReturn(response);
        OrderIdempotencyService service = service(orderService);

        var first = service.createOrder("checkout-123", request(2));
        var replay = service.createOrder("checkout-123", request(2));

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(response, replay.response());
        verify(orderService, times(1)).createOrder(request(2));
    }

    @Test
    void shouldRejectDifferentPayloadAndConcurrentRequest() throws Exception {
        OrderService orderService = mock(OrderService.class);
        CountDownLatch creationStarted = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        when(orderService.createOrder(request(2))).thenAnswer(invocation -> {
            creationStarted.countDown();
            assertTrue(allowCompletion.await(5, TimeUnit.SECONDS));
            return response(100L);
        });
        OrderIdempotencyService service = service(orderService);

        Future<OrderIdempotencyService.Result> first = executor.submit(
                () -> service.createOrder("checkout-123", request(2)));
        assertTrue(creationStarted.await(5, TimeUnit.SECONDS));

        ResponseStatusException concurrent = assertThrows(ResponseStatusException.class,
                () -> service.createOrder("checkout-123", request(2)));
        ResponseStatusException differentPayload = assertThrows(ResponseStatusException.class,
                () -> service.createOrder("checkout-123", request(1)));

        assertEquals(HttpStatus.CONFLICT, concurrent.getStatusCode());
        assertEquals("A request with this Idempotency-Key is already being processed",
                concurrent.getReason());
        assertEquals(HttpStatus.CONFLICT, differentPayload.getStatusCode());
        assertEquals("Idempotency-Key has already been used with a different request",
                differentPayload.getReason());

        allowCompletion.countDown();
        assertEquals(100L, first.get(5, TimeUnit.SECONDS).response().id());
        verify(orderService, times(1)).createOrder(any());
    }

    @Test
    void shouldReleaseRedisLockAfterFailedCreation() {
        OrderService orderService = mock(OrderService.class);
        when(orderService.createOrder(request(2)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                        "Insufficient stock"))
                .thenReturn(response(100L));
        OrderIdempotencyService service = service(orderService);

        assertThrows(ResponseStatusException.class,
                () -> service.createOrder("checkout-123", request(2)));
        var retry = service.createOrder("checkout-123", request(2));

        assertFalse(retry.replayed());
        assertEquals(100L, retry.response().id());
        verify(orderService, times(2)).createOrder(request(2));
    }

    @Test
    void shouldAllowNewCreationAfterCompletedEntryExpires() throws Exception {
        OrderService orderService = mock(OrderService.class);
        when(orderService.createOrder(request(2)))
                .thenReturn(response(100L), response(200L));
        OrderIdempotencyService service = service(orderService, Duration.ofSeconds(2),
                Duration.ofMillis(350));

        service.createOrder("checkout-123", request(2));
        assertTrue(service.createOrder("checkout-123", request(2)).replayed());
        String redisKey = onlyRedisKey();

        awaitCondition(() -> Boolean.FALSE.equals(redisTemplate.hasKey(redisKey)),
                Duration.ofSeconds(5));
        var afterExpiry = service.createOrder("checkout-123", request(2));

        assertFalse(afterExpiry.replayed());
        assertEquals(200L, afterExpiry.response().id());
        verify(orderService, times(2)).createOrder(request(2));
    }

    @Test
    void staleAttemptMustNotCompleteAReacquiredProcessingLock() throws Exception {
        OrderService orderService = mock(OrderService.class);
        AtomicInteger invocation = new AtomicInteger();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        when(orderService.createOrder(request(2))).thenAnswer(ignored -> {
            int current = invocation.incrementAndGet();
            if (current == 1) {
                firstStarted.countDown();
                assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
                return response(100L);
            }
            secondStarted.countDown();
            assertTrue(releaseSecond.await(5, TimeUnit.SECONDS));
            return response(200L);
        });
        OrderIdempotencyService service = service(orderService, Duration.ofMillis(350),
                Duration.ofSeconds(5));

        Future<OrderIdempotencyService.Result> first = executor.submit(
                () -> service.createOrder("checkout-123", request(2)));
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        String redisKey = onlyRedisKey();
        awaitCondition(() -> Boolean.FALSE.equals(redisTemplate.hasKey(redisKey)),
                Duration.ofSeconds(5));

        Future<OrderIdempotencyService.Result> second = executor.submit(
                () -> service.createOrder("checkout-123", request(2)));
        assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
        releaseFirst.countDown();
        assertEquals(100L, first.get(5, TimeUnit.SECONDS).response().id());

        ResponseStatusException stillProcessing = assertThrows(ResponseStatusException.class,
                () -> service.createOrder("checkout-123", request(2)));
        assertEquals("A request with this Idempotency-Key is already being processed",
                stillProcessing.getReason());

        releaseSecond.countDown();
        assertEquals(200L, second.get(5, TimeUnit.SECONDS).response().id());
        assertEquals(200L,
                service.createOrder("checkout-123", request(2)).response().id());
        verify(orderService, times(2)).createOrder(request(2));
    }

    private OrderIdempotencyService service(OrderService orderService) {
        return service(orderService, DEFAULT_PROCESSING_TTL, DEFAULT_COMPLETED_TTL);
    }

    private OrderIdempotencyService service(OrderService orderService, Duration processingTtl,
            Duration completedTtl) {
        return new OrderIdempotencyService(orderService, redisTemplate,
                JsonMapper.builder().build(), processingTtl, completedTtl);
    }

    private static void flushRedis() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    private static String onlyRedisKey() {
        Set<String> keys = redisTemplate.keys("idempotency:orders:*");
        assertEquals(1, keys.size());
        return keys.iterator().next();
    }

    private static void awaitCondition(BooleanSupplier condition, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(condition.getAsBoolean(), "Condition was not met within " + timeout);
    }

    private static OrderRequest request(int quantity) {
        return new OrderRequest(1L, 20L, 21L,
                List.of(new OrderItemRequest(10L, quantity)));
    }

    private static OrderResponse response(long id) {
        return new OrderResponse(id, "ORD-" + id, OrderStatus.PROCESSING,
                new BigDecimal("500.00"), "TRY");
    }
}
