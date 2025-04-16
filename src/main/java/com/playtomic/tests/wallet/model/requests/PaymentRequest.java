package com.playtomic.tests.wallet.model.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.playtomic.tests.wallet.model.constants.PaymentMethod;
import com.playtomic.tests.wallet.model.constants.PaymentType;
import com.playtomic.tests.wallet.utils.IdempotencyUtils;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentRequest implements IPaymentRequest {

    // HARDCODED FOR POC
    private final PaymentMethod paymentMethod = PaymentMethod.CARD;
    private final PaymentType paymentType = PaymentType.TOP_UP;


    @NotBlank(message = "Account ID cannot be blank")
    @Size(min = 5, max = 50, message = "Account ID must be between 5-50 characters")
    private final String accountId;

    @NotBlank(message = "Credit card number cannot be blank")
    @Pattern(regexp = "^[0-9]{13,19}$", message = "Invalid credit card number format")
    private final String cardNumber;

    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "1000000.00", message = "Amount cannot exceed 1,000,000.00")
    private final BigDecimal amount;

    @NotBlank(message = "Session ID cannot be blank")
    @Size(min = 10, max = 100, message = "Session ID must be between 10-100 characters")
    private final String sessionId;

    // This is better to be set off on client side generating here for easy of use
    private final String idempotencyKey;

    @JsonCreator
    public PaymentRequest(@JsonProperty(value = "account_id", required = true) String id,
                          @JsonProperty(value = "credit_card", required = true) String card,
                          @JsonProperty(value = "amount", required = true) BigDecimal amount,
                          @JsonProperty(value = "session_id", required = true) String sessionId) {
        this.accountId = id;
        this.cardNumber = card;
        this.amount = amount;
        this.sessionId = sessionId;
        this.idempotencyKey = IdempotencyUtils.generateIdempotenceKey(this);
    }

}
