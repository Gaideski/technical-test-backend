package com.playtomic.tests.wallet.model.requests;

import com.playtomic.tests.wallet.model.constants.PaymentMethod;

import java.math.BigDecimal;

public interface IPaymentRequest {
    String getCardNumber();

    BigDecimal getAmount();
    String getIdempotencyKey();
    String getAccountId();
    PaymentMethod getPaymentMethod();
}
