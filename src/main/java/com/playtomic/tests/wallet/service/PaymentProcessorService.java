package com.playtomic.tests.wallet.service;

import com.playtomic.tests.wallet.model.constants.PaymentGateway;
import com.playtomic.tests.wallet.model.constants.PaymentStatus;
import com.playtomic.tests.wallet.model.exceptions.TransactionNotFoundException;
import com.playtomic.tests.wallet.model.requests.PaymentRequest;
import com.playtomic.tests.wallet.model.responses.DefaultPaymentResponse;
import com.playtomic.tests.wallet.model.responses.IPaymentResponse;
import com.playtomic.tests.wallet.service.gateways.GatewayConnection;
import com.playtomic.tests.wallet.service.registry.PaymentGatewayRegistry;
import com.playtomic.tests.wallet.utils.CardUtils;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@AllArgsConstructor
public class PaymentProcessorService {

    private final PaymentGatewayRegistry paymentGatewayRegistry;
    private final Logger logger = LoggerFactory.getLogger(PaymentProcessorService.class);
    private final TransactionService transactionService;


    public CompletableFuture<IPaymentResponse> requestPaymentForGateway(PaymentRequest paymentRequest, long transactionId) {

        GatewayConnection conn = selectBestProvider();

        // Use circuit breaker here on gateway call
        CircuitBreaker circuitBreaker =
                (CircuitBreaker) conn.getCircuitBreaker();

        return circuitBreaker.executeCompletionStage(
                () -> conn.getPaymentGateway().charge(paymentRequest.getCardNumber(), paymentRequest.getAmount())
        ).toCompletableFuture().exceptionally(throwable -> {
            logger.error("Circuit breaker triggered fallback for transaction {}: {}", transactionId, throwable.getMessage());

            try {
                transactionService.updateTransactionPaymentStatus(transactionId, PaymentStatus.FAILED);
            } catch (TransactionNotFoundException e) {
                logger.warn("Transaction not found during fallback", e);
            }


            return (IPaymentResponse) new DefaultPaymentResponse();
        }).thenApply(response -> {
            try {
                transactionService.setProviderForTransaction(transactionId,
                        CardUtils.maskCardNumber(paymentRequest.getCardNumber()),
                        PaymentGateway.STRIPE, response);

                transactionService.updateTransactionPaymentStatus(transactionId,
                        PaymentStatus.PROCESSING);

                if (response.getGatewayTransactionAmount() != null &&
                        response.getGatewayTransactionAmount().compareTo(paymentRequest.getAmount()) == 0) {
                    transactionService.updateTransactionPaymentStatus(transactionId,
                            PaymentStatus.SUCCESSFUL);
                }

            } catch (TransactionNotFoundException e) {
                logger.warn("Transaction not found", e);
                throw new RuntimeException(e);
            }

            return response;
        });


    }

    private GatewayConnection selectBestProvider() {
        // Use some fancying logic to determine the best available provider.
        // Sticking on Stripe for POC
        return paymentGatewayRegistry.getProviderConnection(PaymentGateway.STRIPE);
    }

}
