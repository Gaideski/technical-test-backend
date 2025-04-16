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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class WalletService {
    // Although we can lock the row for update on database side, this lock works only for different connections
    // Since the application may re-use the connection to perform the requests, a software locking is required to
    // prevent the same connection to perform concurrent requests.
    private static final int AMOUNT_LOCK_BUCKETS = 256;
    private static final Lock[] ACCOUNT_LOCK_BUCKET = new ReentrantLock[AMOUNT_LOCK_BUCKETS];

    static {
        for (int i = 0; i < AMOUNT_LOCK_BUCKETS; i++) {
            ACCOUNT_LOCK_BUCKET[i] = new ReentrantLock();
        }
    }

    private final WalletRepository walletRepository;
    private final PaymentProcessorService paymentProcessorService;
    private final TransactionService transactionService;
    private final Logger logger = LoggerFactory.getLogger(WalletService.class);

    public WalletDto getOrCreateWalletByAccountId(String accountId, String sessionId) throws WalletNotFoundException {
        Optional<Wallet> wallet = walletRepository.findByAccountIdWithTransactions(accountId);
        return wallet.isPresent() ? formatWalletForResponse(wallet.get()) : createNewWallet(accountId, sessionId);
    }

    @Transactional
    public WalletDto createNewWallet(String accountId, String sessionId) {
        try {
            Wallet newWallet = new Wallet();
            newWallet.setAmount(BigDecimal.ZERO);
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

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    private Transaction initiateTransaction(PaymentRequest paymentRequest) throws WalletNotFoundException, TransactionIdempotencyViolation {
        var wallet = walletRepository.findByAccountId(paymentRequest.getAccountId())
                .orElseThrow(() -> new WalletNotFoundException(paymentRequest.getAccountId()));

        transactionService.certifyIdempotency(paymentRequest);
        return transactionService.createInitialTransaction(wallet, paymentRequest);
    }

    // Listening to all events here, Ideally different events will go to different listeners.
    // In memory version of queue/async processing.
    @EventListener
    public void handleTransactionStatusChange(TransactionStatusChangedEvent event) throws WalletNotFoundException, TransactionNotFoundException, InterruptedException {
        if (event.getNewStatus() == PaymentStatus.SUCCESSFUL) {
            verifyTransactionAndUpdateFunds(event.getWalletId(), event.getTransactionId());
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    public void verifyTransactionAndUpdateFunds(Long walletId, Long transactionId)
            throws TransactionNotFoundException, WalletNotFoundException, InterruptedException {

        final int MAX_RETRIES = 3;
        int retryCount = 0;

        var lock = getLockFromAccountBucket(walletId);
        if (!lock.tryLock(3, TimeUnit.SECONDS)) {
            throw new RuntimeException("Account lock unavailable");
        }

        try {
            while (retryCount < MAX_RETRIES) {
                try {
                    var transaction = transactionService.findTransactionByIdAndLock(transactionId)
                            .orElseThrow(() -> new TransactionNotFoundException(transactionId));

                    if (transaction.getPaymentStatus() == PaymentStatus.FINALIZED) {
                        return; // Already processed
                    }

                    Wallet currentWallet = walletRepository.findById(walletId)
                            .orElseThrow(() -> new WalletNotFoundException("Wallet not found"));

                    int updatedRows = walletRepository.addFundsWithVersionCheck(
                            walletId,
                            transaction.getAmount(),
                            currentWallet.getVersion()
                    );

                    if (updatedRows == 0) {
                        // Version conflict occurred
                        logger.warn("Conflict happened while updating wallet");
                        throw new OptimisticLockException("Wallet version conflict");
                    }

                    // 4. Update transaction status
                    transactionService.finalizeTransaction(transaction);

                    logger.info("Atomic update succeeded - walletId {}, added: {}. TransactionId: {}",
                            walletId, transaction.getAmount(), transactionId);

                    return;

                } catch (OptimisticLockException | InvalidTransactionStatusException e) {
                    retryCount++;
                    logger.warn("Optimistic lock conflict (attempt {}/{}), walletId: {}",
                            retryCount, MAX_RETRIES, walletId);

                    if (retryCount >= MAX_RETRIES) {
                        throw new IllegalStateException("Failed after " + MAX_RETRIES + " attempts", e);
                    }

                    Thread.sleep(100 * (long) Math.pow(2, retryCount));
                }
            }
        } finally {
            lock.unlock();
        }
    }

    // Get lock using hashcode. Getting rid of any negative values
    private Lock getLockFromAccountBucket(Long lockId) {
        return ACCOUNT_LOCK_BUCKET[(lockId.hashCode() & 0x7FFFFFFF) % AMOUNT_LOCK_BUCKETS];
    }
}
