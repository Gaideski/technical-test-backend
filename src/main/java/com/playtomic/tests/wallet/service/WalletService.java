package com.playtomic.tests.wallet.service;

import com.playtomic.tests.wallet.model.constants.PaymentStatus;
import com.playtomic.tests.wallet.model.dto.Transaction;
import com.playtomic.tests.wallet.model.dto.TransactionDto;
import com.playtomic.tests.wallet.model.dto.Wallet;
import com.playtomic.tests.wallet.model.dto.WalletDto;
import com.playtomic.tests.wallet.model.eventpublisher.TransactionStatusChangedEvent;
import com.playtomic.tests.wallet.model.exceptions.InvalidTransactionStatusException;
import com.playtomic.tests.wallet.model.exceptions.TransactionIdempotencyViolation;
import com.playtomic.tests.wallet.model.exceptions.TransactionNotFoundException;
import com.playtomic.tests.wallet.model.exceptions.WalletNotFoundException;
import com.playtomic.tests.wallet.model.requests.PaymentRequest;
import com.playtomic.tests.wallet.repository.WalletRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
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

    public TransactionDto rechargeWallet(PaymentRequest paymentRequest) throws WalletNotFoundException, InvalidTransactionStatusException, TransactionIdempotencyViolation {
        // sync submission to database
        var transaction = initiateTransaction(paymentRequest);
        // Async flow
        paymentProcessorService.requestPaymentForGateway(paymentRequest, transaction.getTransactionId());
        return new TransactionDto(transaction);
    }

    @Transactional
    private Transaction initiateTransaction(PaymentRequest paymentRequest) throws WalletNotFoundException, InvalidTransactionStatusException, TransactionIdempotencyViolation {
        var wallet = walletRepository.findByAccountId(paymentRequest.getAccountId())
                .orElseThrow(() -> new WalletNotFoundException(paymentRequest.getAccountId()));

        transactionService.certifyIdempotency(paymentRequest);
        return transactionService.createInitialTransaction(wallet, paymentRequest);
    }

    // Listening to all events here, Ideally different events will go to different listeners.
    // In memory version of queue/async processing.
    @EventListener
    public void handleTransactionStatusChange(TransactionStatusChangedEvent event) throws WalletNotFoundException, TransactionNotFoundException {
        if (event.getNewStatus() == PaymentStatus.SUCCESSFUL) {
            verifyTransactionAndUpdateFunds(event.getWalletId(), event.getTransactionId());
        }
    }

    @Transactional
    private void verifyTransactionAndUpdateFunds(Long walletId, Long transactionId) throws TransactionNotFoundException, WalletNotFoundException {
        final int MAX_RETRIES = 3;
        int retryCount = 0;

        while (retryCount < MAX_RETRIES) {
            try {
                // Get the latest transaction state
                var transaction = transactionService.findTransactionById(transactionId);

                if (transaction.getPaymentStatus().equals(PaymentStatus.SUCCESSFUL) &&
                        transaction.getPaymentGatewayTransactionId() != null &&
                        !transaction.getPaymentGatewayTransactionId().isEmpty()) {
                    // Get the latest wallet state
                    Wallet currentWallet = walletRepository.findById(walletId)
                            .orElseThrow(() -> new WalletNotFoundException("Wallet not found"));

                    currentWallet.setFunds(currentWallet.getFunds().add(transaction.getAmount()));

                    walletRepository.save(currentWallet);

                    transactionService.finalizeTransaction(transaction);

                    return;

                } else {
                    // Transaction status doesn't require action here
                }
            } catch (OptimisticLockException | InvalidTransactionStatusException e) {
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
