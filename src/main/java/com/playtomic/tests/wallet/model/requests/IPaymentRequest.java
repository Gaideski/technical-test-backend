package com.playtomic.tests.wallet.model.requests;

import java.math.BigDecimal;

public interface IPaymentRequest {
    String getCardNumber();

    BigDecimal getAmount();
}
