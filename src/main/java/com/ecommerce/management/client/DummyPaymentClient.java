package com.ecommerce.management.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.dto.payment.provider.DummyPaymentAcceptedResponse;
import com.ecommerce.management.dto.payment.provider.DummyPaymentRequest;

@Component
public class DummyPaymentClient {

    private final RestClient restClient;

    public DummyPaymentClient(@Value("${payment.provider.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public DummyPaymentAcceptedResponse createPayment(DummyPaymentRequest request) {
        try {
            return restClient.post()
                    .uri("/api/payments")
                    .body(request)
                    .retrieve()
                    .body(DummyPaymentAcceptedResponse.class);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Payment provider could not be reached",
                    exception
            );
        }
    }
}
