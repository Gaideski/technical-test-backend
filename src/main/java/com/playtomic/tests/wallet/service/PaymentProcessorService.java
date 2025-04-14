package com.playtomic.tests.wallet.service;

import com.playtomic.tests.wallet.model.constants.PaymentGatewayProvider;
import com.playtomic.tests.wallet.model.constants.PaymentStatus;
import com.playtomic.tests.wallet.model.exceptions.TransactionNotFoundException;
import com.playtomic.tests.wallet.model.requests.PaymentRequest;
import com.playtomic.tests.wallet.service.gateways.GatewayConnection;
import com.playtomic.tests.wallet.service.registry.PaymentGatewayRegistry;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@AllArgsConstructor
public class PaymentProcessorService {

    private final PaymentGatewayRegistry paymentGatewayRegistry;
    private final Logger logger = LoggerFactory.getLogger(PaymentProcessorService.class);
    private final TransactionService transactionService;
    private final TransactionTemplate transactionTemplate;


    public void requestPaymentForGateway(PaymentRequest paymentRequest, long transactionId) throws TransactionNotFoundException {

        GatewayConnection conn = selectBestProvider();

        var response = conn.getPaymentGateway().charge(paymentRequest.getCardNumber(), paymentRequest.getAmount()).join();
        //.thenApplyAsync(response -> {
        logger.debug("Processing gateway response for transaction {}", transactionId);


        logger.info("Updating transaction {} to PROCESSING", transactionId);

        transactionService.setProviderForTransaction(transactionId,
                PaymentGatewayProvider.STRIPE, response);

        transactionService.updateTransactionPaymentStatus(transactionId,
                PaymentStatus.PROCESSING);

        if (response.getGatewayTransactionAmount() != null &&
                response.getGatewayTransactionAmount().compareTo(paymentRequest.getAmount()) == 0) {
            logger.info("Updating transaction {} to SUCCESSFUL", transactionId);
            transactionService.updateTransactionPaymentStatus(transactionId,
                    PaymentStatus.SUCCESSFUL);
        }


        //                )
//                .exceptionally(ex -> {
//                    logger.error("Payment processing failed", ex);
//                    throw new CompletionException(ex);
//                });
    }

    private GatewayConnection selectBestProvider() {
        // Use some fancying logic to determine the best available provider.
        // Sticking on Stripe for POC
        return paymentGatewayRegistry.getProviderConnection(PaymentGatewayProvider.STRIPE);
    }

}
