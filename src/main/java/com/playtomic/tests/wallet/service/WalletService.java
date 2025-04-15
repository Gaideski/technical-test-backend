package com.playtomic.tests.wallet.service;

import com.playtomic.tests.wallet.model.constants.PaymentStatus;
import com.playtomic.tests.wallet.model.dto.Transaction;
import com.playtomic.tests.wallet.model.dto.Wallet;
import com.playtomic.tests.wallet.model.exceptions.TransactionNotFoundException;
import com.playtomic.tests.wallet.model.exceptions.WalletNotFoundException;
import com.playtomic.tests.wallet.model.requests.PaymentRequest;
import com.playtomic.tests.wallet.model.dto.WalletDto;
import com.playtomic.tests.wallet.repository.WalletRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
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

    //TODO: create validations:


    public WalletDto getOrCreateWalletByAccountId(String accountId, String sessionId) throws WalletNotFoundException {
        Optional<Wallet> wallet = walletRepository.findByAccountIdWithTransactions(accountId);
        return wallet.isPresent() ? formatWalletForResponse(wallet.get()) : createNewWallet(accountId, sessionId);
    }

    @Transactional
    public WalletDto createNewWallet(String accountId, String sessionId) throws WalletNotFoundException {
        try {
            Wallet newWallet = new Wallet();
            newWallet.setFunds(BigDecimal.ZERO);
            newWallet.setAccountId(accountId);
            walletRepository.save(newWallet);
            return formatWalletForResponse(walletRepository.save(newWallet));
        } catch (DataAccessException ex) {
            logger.error("Failed to create wallet for account {} under session: {}", accountId, sessionId, ex);
            return null;
        }
    }

    private WalletDto formatWalletForResponse(Wallet wallet) {
        return new WalletDto(wallet);
    }

    public void depositFundsToAccount(PaymentRequest paymentRequest) throws WalletNotFoundException {
        // sync submission to database
        var transaction = initiateTransaction(paymentRequest);

        // Async flow
        paymentProcessorService.requestPaymentForGateway(paymentRequest, transaction.getTransactionId())
                .thenApply(
                        response -> {
                            try {
                                verifyTransactionAndUpdateFunds(transaction.getWallet().getWalletId(), transaction.getTransactionId());
                            } catch (TransactionNotFoundException | WalletNotFoundException e) {
                                throw new RuntimeException(e);
                            }
                            return null;
                        }
                );
    }

    private Transaction initiateTransaction(PaymentRequest paymentRequest) throws WalletNotFoundException {
        var wallet = walletRepository.findByAccountId(paymentRequest.getAccountId())
                .orElseThrow(() -> new WalletNotFoundException(paymentRequest.getAccountId()));

        return transactionService.createInitialTransaction(wallet, paymentRequest);
    }

    @Transactional
    private void verifyTransactionAndUpdateFunds(Long walletId, Long transactionId) throws TransactionNotFoundException, WalletNotFoundException {
        final int MAX_RETRIES = 3;
        int retryCount = 0;

        while (retryCount < MAX_RETRIES) {
            try {
                // Get the latest transaction state
                var transaction = transactionService.findTransactionById(transactionId);

                if (transaction.getPaymentStatus().equals(PaymentStatus.SUCCESSFUL)) {
                    // Get the latest wallet state
                    Wallet currentWallet = walletRepository.findById(walletId)
                            .orElseThrow(() -> new WalletNotFoundException("Wallet not found"));

                    currentWallet.setFunds(currentWallet.getFunds().add(transaction.getAmount()));

                    walletRepository.save(currentWallet);

                    transactionService.finalizeTransaction(transaction);

                    return;
                } else {
                    // Transaction status doesn't require action here
                    return;
                }
            } catch (OptimisticLockException e) {
                // Optimistic lock exception - another process modified the wallet
                retryCount++;
                if (retryCount >= MAX_RETRIES) {
                    throw new IllegalStateException("Failed to update wallet funds after " + MAX_RETRIES + " attempts", e);
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Retry interrupted", ie);
                }
            }
        }
    }


}
