package com.playtomic.tests.wallet.model.responses;

import java.math.BigDecimal;

public class DefaultPaymentResponse implements IPaymentResponse {

    @Override
    public String getGatewayTransactionID() {
        return "";
    }

    @Override
    public BigDecimal getGatewayTransactionAmount() {
        return null;
    }
}
