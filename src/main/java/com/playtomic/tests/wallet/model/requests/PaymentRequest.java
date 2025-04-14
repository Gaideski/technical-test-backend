package com.playtomic.tests.wallet.model.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playtomic.tests.wallet.model.constants.PaymentMethod;
import lombok.Getter;
import lombok.NonNull;

import java.math.BigDecimal;

@Getter
public class PaymentRequest {

    // HARDCODED FOR POC
    private final @NonNull PaymentMethod paymentMethod = PaymentMethod.CARD;
    private final @NonNull String accountId;
    private final @NonNull String cardNumber;
    private final @NonNull BigDecimal amount;
    private final @NonNull String sessionId;


    @JsonCreator
    public PaymentRequest(@JsonProperty(value = "account_id", required = true) String id,
                          @JsonProperty(value = "credit_card", required = true) String card,
                          @JsonProperty(value = "amount", required = true) BigDecimal amount,
                          @JsonProperty(value = "session_id", required = true) String sessionId) {
        this.accountId = id;
        this.cardNumber = card;
        this.amount = amount;
        this.sessionId = sessionId;
    }

}
