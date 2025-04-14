package com.playtomic.tests.wallet.model.responses.stripe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playtomic.tests.wallet.model.responses.IPaymentResponse;
import lombok.NonNull;

import java.math.BigDecimal;


public class StripePaymentResponse implements IPaymentResponse {
    private final @NonNull String id;
    private final BigDecimal amount;

    @JsonCreator
    public StripePaymentResponse(@JsonProperty(value = "id", required = true) String id,
                                 @JsonProperty(value = "amount") BigDecimal amount) {
        this.id = id;
        this.amount = amount;
    }

    @Override
    public String getGatewayTransactionID() {
        return this.id;
    }

    @Override
    public BigDecimal getGatewayTransactionAmount() {
        return this.amount;
    }
}