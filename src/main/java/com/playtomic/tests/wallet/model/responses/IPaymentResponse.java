package com.playtomic.tests.wallet.model.responses;

import java.math.BigDecimal;

public interface IPaymentResponse {
    String getGatewayTransactionID();

    BigDecimal getGatewayTransactionAmount();
}
