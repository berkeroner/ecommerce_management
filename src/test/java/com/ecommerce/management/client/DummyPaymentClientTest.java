package com.ecommerce.management.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.ecommerce.management.dto.payment.provider.DummyPaymentItemRequest;
import com.ecommerce.management.dto.payment.provider.DummyPaymentRequest;
import com.ecommerce.management.dto.payment.provider.ProviderPaymentStatus;
import com.sun.net.httpserver.HttpServer;

class DummyPaymentClientTest {

    @Test
    void shouldSendItemListAndReadAcceptedResponse() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/payments", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "paymentId": "11111111-1111-1111-1111-111111111111",
                      "orderId": "42",
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

            var response = client.createPayment(new DummyPaymentRequest(
                    "42",
                    List.of(new DummyPaymentItemRequest(
                            "Kulaklik", 2, new BigDecimal("750.00"))),
                    "TRY"
            ));

            assertThat(response.orderId()).isEqualTo("42");
            assertThat(response.status()).isEqualTo(ProviderPaymentStatus.PROCESSING);
            assertThat(requestBody.get()).contains(
                    "\"orderId\":\"42\"",
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
