package com.playtomic.tests.wallet.service;

import com.playtomic.tests.wallet.model.constants.PaymentStatus;
import com.playtomic.tests.wallet.model.dto.Wallet;
import com.playtomic.tests.wallet.model.exceptions.TransactionNotFoundException;
import com.playtomic.tests.wallet.model.exceptions.WalletNotFoundException;
import com.playtomic.tests.wallet.model.requests.PaymentRequest;
import com.playtomic.tests.wallet.model.responses.WalletResponse;
import com.playtomic.tests.wallet.repository.WalletRepository;
import jakarta.transaction.RollbackException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
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

    //TODO: create validations:


    public Optional<WalletResponse> getOrCreateWalletByAccountId(String accountId, String sessionId) {
        Optional<Wallet> wallet = walletRepository.findByAccountIdWithTransactions(accountId);
        return wallet.isPresent() ? formatWalletForResponse(wallet) : createNewWallet(accountId, sessionId);
    }

    @Transactional
    public Optional<WalletResponse> createNewWallet(String accountId, String sessionId) {
        try {
            Wallet newWallet = new Wallet();
            newWallet.setFunds(BigDecimal.ZERO);
            newWallet.setAccountId(accountId);
            walletRepository.save(newWallet);
            return formatWalletForResponse(walletRepository.findByAccountIdWithTransactions(accountId));
        } catch (DataAccessException ex) {
            logger.error("Failed to create wallet for account {} under session: {}", accountId, sessionId, ex);
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

        // Async flow
        paymentProcessorService.requestPaymentForGateway(paymentRequest, transaction.getTransactionId())
                .thenApply(
                        response -> {
                            try {
                                verifyTransactionAndUpdateFunds(wallet, transaction.getTransactionId());
                            } catch (TransactionNotFoundException | RollbackException | WalletNotFoundException e) {
                                throw new RuntimeException(e);
                            }
                            return null;
                        }
                );
    }


    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRES_NEW)
    private void verifyTransactionAndUpdateFunds(Wallet wallet, Long transactionId) throws TransactionNotFoundException, RollbackException, WalletNotFoundException {
        //Todo: Hold account here using locking to update the amount
        var transaction = transactionService.findTransactionById(transactionId);

        if (transaction.getPaymentStatus().equals(PaymentStatus.SUCCESSFUL)) {
            walletRepository.findAndLockById(wallet.getWalletId())
                    .orElseThrow(() -> new WalletNotFoundException(wallet.getWalletId().toString()));

            int updated = walletRepository.addFunds(wallet.getWalletId(), transaction.getAmount().longValue());

            if (updated != 1) {
                //TODO: Doesn't rollback, fix it!
                throw new IllegalStateException("Failed to update wallet funds");
            }
            transactionService.finalizeTransaction(transaction);
        }
    }


}
