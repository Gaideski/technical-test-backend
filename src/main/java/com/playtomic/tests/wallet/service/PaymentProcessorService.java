package com.playtomic.tests.wallet.service;

import com.playtomic.tests.wallet.model.constants.PaymentGateway;
import com.playtomic.tests.wallet.model.constants.PaymentStatus;
import com.playtomic.tests.wallet.model.exceptions.TransactionNotFoundException;
import com.playtomic.tests.wallet.model.requests.PaymentRequest;
import com.playtomic.tests.wallet.model.responses.IPaymentResponse;
import com.playtomic.tests.wallet.service.gateways.GatewayConnection;
import com.playtomic.tests.wallet.service.registry.PaymentGatewayRegistry;
import com.playtomic.tests.wallet.utils.CardUtils;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CompletableFuture;

@Service
@AllArgsConstructor
public class PaymentProcessorService {

    private final PaymentGatewayRegistry paymentGatewayRegistry;
    private final Logger logger = LoggerFactory.getLogger(PaymentProcessorService.class);
    private final TransactionService transactionService;
    private final TransactionTemplate transactionTemplate;


    public CompletableFuture<IPaymentResponse> requestPaymentForGateway(PaymentRequest paymentRequest, long transactionId) throws TransactionNotFoundException {

        GatewayConnection conn = selectBestProvider();
        // todo: Use circuit breaker here on gateway call
        return conn.getPaymentGateway().charge(paymentRequest.getCardNumber(), paymentRequest.getAmount()).thenApply(response -> {
            try {
                transactionService.setProviderForTransaction(transactionId,
                        CardUtils.maskCardNumber(paymentRequest.getCardNumber()),
                        PaymentGateway.STRIPE, response);

                transactionService.updateTransactionPaymentStatus(transactionId,
                        PaymentStatus.PROCESSING);

                if (response.getGatewayTransactionAmount() != null &&
                        response.getGatewayTransactionAmount().compareTo(paymentRequest.getAmount()) == 0) {
                    logger.info("Updating transaction {} to SUCCESSFUL", transactionId);
                    transactionService.updateTransactionPaymentStatus(transactionId,
                            PaymentStatus.SUCCESSFUL);
                }

            } catch (TransactionNotFoundException e) {
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
