package com.ecommerce.management.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.ecommerce.management.dto.payment.provider.DummyPaymentItemRequest;
import com.ecommerce.management.dto.payment.provider.DummyPaymentRequest;
import com.ecommerce.management.dto.payment.provider.ProviderPaymentStatus;
import com.ecommerce.management.entity.enums.PaymentMethod;
import com.sun.net.httpserver.HttpServer;

class DummyPaymentClientTest {

    @Test
    void shouldSendItemListAndReadAcceptedResponse() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> idempotencyHeader = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/payments", exchange -> {
            idempotencyHeader.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "paymentId": "11111111-1111-1111-1111-111111111111",
                      "orderId": "42",
                      "idempotencyKey": "22222222-2222-2222-2222-222222222222",
                      "status": "PROCESSING"
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(202, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            DummyPaymentClient client = new DummyPaymentClient(
                    "http://localhost:" + server.getAddress().getPort());

            UUID idempotencyKey = UUID.fromString("22222222-2222-2222-2222-222222222222");
            var response = client.createPayment(idempotencyKey, new DummyPaymentRequest(
                    "42",
                    PaymentMethod.CREDIT_CARD,
                    "secret-token",
                    List.of(new DummyPaymentItemRequest(
                            "Kulaklik", 2, new BigDecimal("750.00"))),
                    "TRY"
            ));

            assertThat(response.orderId()).isEqualTo("42");
            assertThat(response.idempotencyKey()).isEqualTo(idempotencyKey);
            assertThat(idempotencyHeader.get()).isEqualTo(idempotencyKey.toString());
            assertThat(response.status()).isEqualTo(ProviderPaymentStatus.PROCESSING);
            assertThat(requestBody.get()).contains(
                    "\"orderId\":\"42\"",
                    "\"method\":\"credit_card\"",
                    "\"paymentToken\":\"secret-token\"",
                    "\"items\"",
                    "\"productName\":\"Kulaklik\"",
                    "\"quantity\":2",
                    "\"unitPrice\":750.00"
            );
        } finally {
            server.stop(0);
        }
    }
}
