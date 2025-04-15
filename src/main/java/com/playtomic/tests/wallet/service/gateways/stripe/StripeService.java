package com.playtomic.tests.wallet.service.gateways.stripe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.playtomic.tests.wallet.model.annotation.PaymentService;
import com.playtomic.tests.wallet.model.constants.PaymentGateway;
import com.playtomic.tests.wallet.model.exceptions.StripeServiceException;
import com.playtomic.tests.wallet.model.responses.IPaymentResponse;
import com.playtomic.tests.wallet.model.responses.stripe.StripePaymentResponse;
import com.playtomic.tests.wallet.service.gateways.IPaymentGatewayService;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;


/**
 * Handles the communication with Stripe.
 * <p>
 * A real implementation would call to String using their API/SDK.
 * This dummy implementation throws an error when trying to charge less than 10€.
 */
@PaymentService(PaymentGateway.STRIPE)
@Service
public class StripeService implements IPaymentGatewayService {

    @NonNull
    private final URI chargesUri;

    @NonNull
    private final URI refundsUri;

    @NonNull
    private final RestTemplate restTemplate;

    public StripeService(@Qualifier("stripeChargesUri") @NonNull URI chargesUri,
                         @Qualifier("stripeRefundsUri") @NonNull URI refundsUri,
                         @NonNull RestTemplateBuilder restTemplateBuilder) {
        this.chargesUri = chargesUri;
        this.refundsUri = refundsUri;
        this.restTemplate =
                restTemplateBuilder
                        .errorHandler(new StripeRestTemplateResponseErrorHandler())
                        .setConnectTimeout(Duration.of(5, ChronoUnit.SECONDS))
                        .build();
    }

    /**
     * Charges money in the credit card.
     * <p>
     * Ignore the fact that no CVC or expiration date are provided.
     *
     * @param creditCardNumber The number of the credit card
     * @param amount           The amount that will be charged.
     * @throws StripeServiceException
     */
    @Async
    @Override
    public CompletableFuture<IPaymentResponse> charge(@NonNull String creditCardNumber, @NonNull BigDecimal amount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ChargeRequest body = new ChargeRequest(creditCardNumber, amount);
                return restTemplate.postForObject(chargesUri, body, StripePaymentResponse.class);
            } catch (RestClientException e) {
                throw new CompletionException(new StripeServiceException());
            }
        });
    }

    /**
     * Refunds the specified payment.
     */
    public void refund(@NonNull String paymentId) throws StripeServiceException {
        // Object.class because we don't read the body here.
        restTemplate.postForEntity(chargesUri.toString(), null, Object.class, paymentId);
    }

    @AllArgsConstructor
    private static class ChargeRequest {

        @NonNull
        @JsonProperty("credit_card")
        String creditCardNumber;

        @NonNull
        @JsonProperty("amount")
        BigDecimal amount;
    }
}
