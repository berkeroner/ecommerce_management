package com.ecommerce.management.client;

import java.util.UUID;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.dto.payment.provider.DummyPaymentAcceptedResponse;
import com.ecommerce.management.dto.payment.provider.DummyPaymentRequest;
import com.ecommerce.management.dto.payment.provider.DummyRefundRequest;
import com.ecommerce.management.dto.payment.provider.DummyRefundResponse;
import com.ecommerce.management.dto.payment.provider.ProviderPaymentStatusResponse;
import com.ecommerce.management.entity.enums.PaymentMethod;

@Component
public class DummyPaymentClient {

    private final RestClient restClient;

    public DummyPaymentClient(@Value("${payment.provider.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public DummyPaymentAcceptedResponse createPayment(
            UUID idempotencyKey, DummyPaymentRequest request) {
        try {
            return restClient.post()
                    .uri("/api/payments")
                    .header("Idempotency-Key", idempotencyKey.toString())
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

    public DummyRefundResponse refundPayment(
            UUID providerPaymentId, PaymentMethod method) {
        try {
            return restClient.post()
                    .uri("/api/payments/{paymentId}/refunds", providerPaymentId)
                    .body(new DummyRefundRequest(method))
                    .retrieve()
                    .body(DummyRefundResponse.class);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Payment provider refund failed",
                    exception
            );
        }
    }

    public Optional<ProviderPaymentStatusResponse> findPayment(UUID providerPaymentId) {
        return getPaymentStatus("/api/payments/{id}", providerPaymentId);
    }

    public Optional<ProviderPaymentStatusResponse> findPaymentByIdempotencyKey(
            UUID idempotencyKey) {
        return getPaymentStatus(
                "/api/payments/by-idempotency-key/{id}", idempotencyKey);
    }

    private Optional<ProviderPaymentStatusResponse> getPaymentStatus(String uri, UUID id) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri(uri, id)
                    .retrieve()
                    .body(ProviderPaymentStatusResponse.class));
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Payment provider status could not be retrieved",
                    exception
            );
        }
    }
}
