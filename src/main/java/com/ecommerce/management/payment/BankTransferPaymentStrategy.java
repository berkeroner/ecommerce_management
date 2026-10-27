package com.ecommerce.management.payment;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.management.client.DummyPaymentClient;
import com.ecommerce.management.dto.payment.PaymentRequest;
import com.ecommerce.management.entity.enums.PaymentMethod;

@Component
public class BankTransferPaymentStrategy extends AbstractDummyPaymentStrategy {

    public BankTransferPaymentStrategy(DummyPaymentClient client) {
        super(client);
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.BANK_TRANSFER;
    }

    @Override
    public void validate(PaymentRequest request) {
        if (hasToken(request.paymentToken())) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "payment_token is not allowed for bank_transfer payments");
        }
    }
}
