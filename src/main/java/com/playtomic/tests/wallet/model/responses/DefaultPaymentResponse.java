package com.playtomic.tests.wallet.model.responses;

import java.math.BigDecimal;

public class DefaultPaymentResponse implements IPaymentResponse {

    @Override
    public String getGatewayTransactionID() {
        return null;
    }

    @Override
    public BigDecimal getGatewayTransactionAmount() {
        return BigDecimal.ZERO;
    }
}
