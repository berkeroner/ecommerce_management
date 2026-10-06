package com.ecommerce.management.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.dto.order.OrderRequest;
import com.ecommerce.management.dto.order.OrderResponse;

import tools.jackson.databind.ObjectMapper;

@Service
public class OrderIdempotencyService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderIdempotencyService.class);
    private static final String KEY_PREFIX = "idempotency:orders:";
    private static final int MAX_KEY_LENGTH = 128;

    private static final DefaultRedisScript<Long> REPLACE_IF_UNCHANGED = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                redis.call('set', KEYS[1], ARGV[2], 'PX', ARGV[3])
                return 1
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> DELETE_IF_UNCHANGED = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final OrderService orderService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration processingTtl;
    private final Duration completedTtl;

    public OrderIdempotencyService(
            OrderService orderService,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${order.idempotency.processing-ttl:5m}") Duration processingTtl,
            @Value("${order.idempotency.ttl:24h}") Duration completedTtl) {
        this.orderService = orderService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.processingTtl = processingTtl;
        this.completedTtl = completedTtl;
    }

    public Result createOrder(String idempotencyKey, OrderRequest request) {
        validateKey(idempotencyKey);

        String redisKey = KEY_PREFIX + sha256(idempotencyKey.trim());
        String requestHash = sha256(objectMapper.writeValueAsBytes(request));
        String processingValue = serialize(new Entry(requestHash, State.PROCESSING, null));

        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, processingValue, processingTtl);
            if (Boolean.FALSE.equals(acquired)) {
                return replayOrReject(redisKey, requestHash);
            }
            if (!Boolean.TRUE.equals(acquired)) {
                throw redisUnavailable();
            }
        } catch (DataAccessException exception) {
            throw redisUnavailable(exception);
        }

        OrderResponse response;
        try {
            response = orderService.createOrder(request);
        } catch (RuntimeException exception) {
            release(redisKey, processingValue);
            throw exception;
        }
        complete(redisKey, processingValue, requestHash, response);
        return new Result(response, false);
    }

    private Result replayOrReject(String redisKey, String requestHash) {
        String storedValue;
        try {
            storedValue = redisTemplate.opsForValue().get(redisKey);
        } catch (DataAccessException exception) {
            throw redisUnavailable(exception);
        }

        // The key may expire between SET NX and GET. A client can safely retry.
        if (storedValue == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency key expired while the request was being checked; retry the request");
        }

        Entry entry = deserialize(storedValue);
        if (!entry.requestHash().equals(requestHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency-Key has already been used with a different request");
        }
        if (entry.state() == State.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A request with this Idempotency-Key is already being processed");
        }
        if (entry.response() == null) {
            throw new IllegalStateException("Completed idempotency entry has no response");
        }
        return new Result(entry.response(), true);
    }

    private void complete(String redisKey, String processingValue, String requestHash,
            OrderResponse response) {
        String completedValue = serialize(new Entry(requestHash, State.COMPLETED, response));
        try {
            Long replaced = redisTemplate.execute(REPLACE_IF_UNCHANGED, java.util.List.of(redisKey),
                    processingValue, completedValue, Long.toString(completedTtl.toMillis()));
            if (replaced == null || replaced != 1L) {
                LOG.error("Could not complete order idempotency entry for key {}", redisKey);
            }
        } catch (DataAccessException exception) {
            // The order has already committed. Do not turn a successful creation into a 5xx response.
            LOG.error("Could not persist completed order idempotency entry for key {}", redisKey,
                    exception);
        }
    }

    private void release(String redisKey, String processingValue) {
        try {
            redisTemplate.execute(DELETE_IF_UNCHANGED, java.util.List.of(redisKey), processingValue);
        } catch (DataAccessException exception) {
            LOG.error("Could not release failed order idempotency entry for key {}", redisKey,
                    exception);
        }
    }

    private String serialize(Entry entry) {
        return objectMapper.writeValueAsString(entry);
    }

    private Entry deserialize(String value) {
        try {
            return objectMapper.readValue(value, Entry.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid order idempotency entry", exception);
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key header must not be blank");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key header must not exceed " + MAX_KEY_LENGTH + " characters");
        }
    }

    private String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private ResponseStatusException redisUnavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Idempotency service is unavailable");
    }

    private ResponseStatusException redisUnavailable(DataAccessException cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Idempotency service is unavailable", cause);
    }

    public record Result(OrderResponse response, boolean replayed) {
    }

    private record Entry(String requestHash, State state, OrderResponse response) {
    }

    private enum State {
        PROCESSING,
        COMPLETED
    }
}
