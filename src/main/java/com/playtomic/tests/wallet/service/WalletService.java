package com.playtomic.tests.wallet.service;

import com.playtomic.tests.wallet.model.constants.PaymentStatus;
import com.playtomic.tests.wallet.model.dto.Wallet;
import com.playtomic.tests.wallet.model.exceptions.TransactionNotFoundException;
import com.playtomic.tests.wallet.model.exceptions.WalletNotFoundException;
import com.playtomic.tests.wallet.model.requests.PaymentRequest;
import com.playtomic.tests.wallet.model.responses.WalletResponse;
import com.playtomic.tests.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletService {
    private final WalletRepository walletRepository;
    private final PaymentProcessorService paymentProcessorService;
    private final TransactionService transactionService;
    private final Logger logger = LoggerFactory.getLogger(WalletService.class);
    @Autowired
    private PlatformTransactionManager transactionManager;

    public Optional<WalletResponse> getOrCreateWalletByAccountId(String accountId) {
        Optional<Wallet> wallet = walletRepository.findByAccountIdWithTransactions(accountId);
        return wallet.isPresent() ? formatWalletForResponse(wallet) : createNewWallet(accountId);
    }

    @Transactional
    public Optional<WalletResponse> createNewWallet(String accountId) {
        try {
            Wallet newWallet = new Wallet();
            newWallet.setFunds(BigDecimal.ZERO);
            newWallet.setAccountId(accountId);
            walletRepository.save(newWallet);
            return formatWalletForResponse(walletRepository.findByAccountIdWithTransactions(accountId));
        } catch (DataAccessException ex) {
            logger.error("Failed to create wallet", ex);
            return Optional.empty();
        }
    }

    private Optional<WalletResponse> formatWalletForResponse(Optional<Wallet> wallet) {
        return wallet.map(value ->
                new WalletResponse(value.getAccountId(),
                        value.getFunds(),
                        value.getTransactions()));
    }

    public void depositFundsToAccount(PaymentRequest paymentRequest) throws WalletNotFoundException, TransactionNotFoundException {
        // sync submission to database
        var wallet = walletRepository.findByAccountId(paymentRequest.getAccountId())
                .orElseThrow(() -> new WalletNotFoundException(paymentRequest.getAccountId()));

        var transaction = transactionService.createInitialTransaction(wallet, paymentRequest);

        // Async process - chain everything together
        paymentProcessorService.requestPaymentForGateway(paymentRequest, transaction.getTransactionId());
//                .thenAcceptAsync(response -> {
//                    try {
        logger.info("Finalizing the transaction");
        //verifyTransactionAndUpdateFunds(wallet, transaction.getTransactionId());
        logger.info("FINISHED1");
//
//                    } catch (TransactionNotFoundException e) {
//                        logger.error("Failed to verify transaction: {}", transaction.getTransactionId(), e);
//                    }
//                })
//                .exceptionally(ex -> {
//                    logger.info("Payment processing failed for transaction: {}, ignoring funds update",
//                            transaction.getTransactionId());
//                    return null;
//                });

    }


    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRES_NEW)
    private void verifyTransactionAndUpdateFunds(Wallet wallet, Long transactionId) throws TransactionNotFoundException {
        var transaction = transactionService.findTransactionById(transactionId);
        if (transaction.getPaymentStatus().equals(PaymentStatus.SUCCESSFUL)) {
            walletRepository.addFunds(wallet.getWalletId(), transaction.getAmount());
            transactionService.finalizeTransaction(transaction);
        }
    }


}
