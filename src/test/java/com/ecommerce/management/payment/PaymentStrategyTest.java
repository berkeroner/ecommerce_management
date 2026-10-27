package com.ecommerce.management.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.client.DummyPaymentClient;
import com.ecommerce.management.dto.payment.PaymentRequest;
import com.ecommerce.management.dto.payment.provider.DummyPaymentAcceptedResponse;
import com.ecommerce.management.dto.payment.provider.DummyPaymentRequest;
import com.ecommerce.management.dto.payment.provider.DummyRefundResponse;
import com.ecommerce.management.dto.payment.provider.ProviderPaymentStatus;
import com.ecommerce.management.entity.enums.PaymentMethod;

@ExtendWith(MockitoExtension.class)
class PaymentStrategyTest {

    @Mock
    private DummyPaymentClient client;

    private CreditCardPaymentStrategy creditCard;
    private BankTransferPaymentStrategy bankTransfer;
    private CashOnDeliveryPaymentStrategy cashOnDelivery;
    private PaymentStrategyFactory factory;

    @BeforeEach
    void setUp() {
        creditCard = new CreditCardPaymentStrategy(client);
        bankTransfer = new BankTransferPaymentStrategy(client);
        cashOnDelivery = new CashOnDeliveryPaymentStrategy(client);
        factory = new PaymentStrategyFactory(List.of(
                creditCard, bankTransfer, cashOnDelivery));
    }

    @Test
    void shouldResolveEverySupportedPaymentMethod() {
        assertSame(creditCard, factory.get(PaymentMethod.CREDIT_CARD));
        assertSame(bankTransfer, factory.get(PaymentMethod.BANK_TRANSFER));
        assertSame(cashOnDelivery, factory.get(PaymentMethod.CASH_ON_DELIVERY));
    }

    @Test
    void shouldRequireTokenForCreditCard() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> creditCard.validate(
                        new PaymentRequest(PaymentMethod.CREDIT_CARD, " ")));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatusCode());
        creditCard.validate(new PaymentRequest(PaymentMethod.CREDIT_CARD, "tok_success"));
    }

    @Test
    void shouldRejectTokenForNonCardMethods() {
        ResponseStatusException bankException = assertThrows(
                ResponseStatusException.class,
                () -> bankTransfer.validate(
                        new PaymentRequest(PaymentMethod.BANK_TRANSFER, "tok_success")));
        ResponseStatusException cashException = assertThrows(
                ResponseStatusException.class,
                () -> cashOnDelivery.validate(
                        new PaymentRequest(PaymentMethod.CASH_ON_DELIVERY, "tok_success")));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, bankException.getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, cashException.getStatusCode());
        bankTransfer.validate(new PaymentRequest(PaymentMethod.BANK_TRANSFER, null));
        cashOnDelivery.validate(new PaymentRequest(PaymentMethod.CASH_ON_DELIVERY, null));
    }

    @Test
    void shouldDelegateProviderOperationsWithTheStrategyMethod() {
        UUID idempotencyKey = UUID.randomUUID();
        UUID providerPaymentId = UUID.randomUUID();
        DummyPaymentRequest providerRequest = new DummyPaymentRequest(
                "42", PaymentMethod.CREDIT_CARD, "tok_success", List.of(), "TRY");
        DummyPaymentAcceptedResponse accepted = new DummyPaymentAcceptedResponse(
                providerPaymentId, "42", idempotencyKey, ProviderPaymentStatus.PROCESSING);
        DummyRefundResponse refunded = new DummyRefundResponse("REF-42");
        when(client.createPayment(idempotencyKey, providerRequest)).thenReturn(accepted);
        when(client.refundPayment(providerPaymentId, PaymentMethod.CREDIT_CARD))
                .thenReturn(refunded);

        assertSame(accepted, creditCard.start(idempotencyKey, providerRequest));
        assertSame(refunded, creditCard.refund(providerPaymentId));
        verify(client).createPayment(idempotencyKey, providerRequest);
        verify(client).refundPayment(providerPaymentId, PaymentMethod.CREDIT_CARD);
    }
}
