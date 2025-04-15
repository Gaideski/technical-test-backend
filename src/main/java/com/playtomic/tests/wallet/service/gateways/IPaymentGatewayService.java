package com.playtomic.tests.wallet.service.gateways;

import com.playtomic.tests.wallet.model.responses.IPaymentResponse;
import lombok.NonNull;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

public interface IPaymentGatewayService {

    CompletableFuture<IPaymentResponse> charge(@NonNull String creditCardNumber, @NonNull BigDecimal amount);

    void refund(@NonNull String paymentId);
}
