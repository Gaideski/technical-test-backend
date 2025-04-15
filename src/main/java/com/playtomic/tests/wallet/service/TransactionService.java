    package com.playtomic.tests.wallet.service;

    import com.playtomic.tests.wallet.model.constants.PaymentGateway;
    import com.playtomic.tests.wallet.model.constants.PaymentStatus;
    import com.playtomic.tests.wallet.model.dto.Transaction;
    import com.playtomic.tests.wallet.model.dto.Wallet;
    import com.playtomic.tests.wallet.model.exceptions.InvalidTransactionStatusException;
    import com.playtomic.tests.wallet.model.exceptions.TransactionIdempotencyViolation;
    import com.playtomic.tests.wallet.model.exceptions.TransactionNotFoundException;
    import com.playtomic.tests.wallet.model.requests.IPaymentRequest;
    import com.playtomic.tests.wallet.model.requests.PaymentRequest;
    import com.playtomic.tests.wallet.model.responses.IPaymentResponse;
    import com.playtomic.tests.wallet.repository.TransactionRepository;
    import lombok.RequiredArgsConstructor;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Isolation;
    import org.springframework.transaction.annotation.Propagation;
    import org.springframework.transaction.annotation.Transactional;

    import java.util.Objects;
    import java.util.Optional;

    @Service
    @RequiredArgsConstructor
    public class TransactionService {
        private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

        private final TransactionRepository transactionRepository;
        private final TransactionStateMachineService transactionStateMachine;

        // High isolation for creation to prevent duplicates
        @Transactional(isolation = Isolation.SERIALIZABLE,
                propagation = Propagation.REQUIRES_NEW,
                timeout = 5) // 5 second timeout
        public Transaction createInitialTransaction(Wallet wallet, PaymentRequest paymentRequest)
                throws InvalidTransactionStatusException {
            Transaction transaction = new Transaction();
            transaction.setWallet(wallet);
            transaction.setAmount(paymentRequest.getAmount());
            transaction.setPaymentMethod(paymentRequest.getPaymentMethod());
            transaction.setPaymentType(paymentRequest.getPaymentType());
            transaction.setIdempotencyKey(paymentRequest.getIdempotencyKey());
            transaction.setPaymentGatewayTransactionId(null);

            return transactionStateMachine.transition(transaction, PaymentStatus.CREATED);
        }

        // Optimistic locking for updates
        @Transactional(isolation = Isolation.READ_COMMITTED)
        public void processPaymentGatewayResponse(long transactionId, String maskedCard,
                                                  PaymentGateway gateway, IPaymentResponse response)
                throws TransactionNotFoundException {

            Transaction transaction = findTransactionById(transactionId);
            updateTransactionGatewayDetails(transaction, maskedCard, gateway, response);
            transitionToSubmittedStatus(transaction);
        }

        // Strong consistency for idempotency checks
        @Transactional(isolation = Isolation.SERIALIZABLE,
                timeout = 3)
        public void certifyIdempotency(IPaymentRequest request) throws TransactionIdempotencyViolation {
            String idempotencyKey = request.getIdempotencyKey();
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("Idempotency key cannot be null or empty");
            }

            // Use pessimistic locking to prevent concurrent duplicates
            Optional<Transaction> existing = transactionRepository
                    .findByIdempotencyKeyWithLock(idempotencyKey);

            if (existing.isPresent()) {
                Transaction transaction = existing.get();
                if (!isMatchingRequest(transaction, request)) {
                    throw new TransactionIdempotencyViolation(
                            "Request parameters don't match existing transaction",
                            transaction,
                            request
                    );
                }
                throw new TransactionIdempotencyViolation(
                        "Duplicate request detected",
                        transaction,
                        request
                );
            }
        }

        private boolean isMatchingRequest(Transaction transaction, IPaymentRequest request) {
            return transaction.getAmount().compareTo(request.getAmount()) == 0 &&
                    transaction.getWallet().getAccountId().equals(request.getAccountId()) &&
                    Objects.equals(transaction.getPaymentMethod(), request.getPaymentMethod());
        }

        // Standard isolation for status updates
        @Transactional(isolation = Isolation.READ_COMMITTED)
        public void finalizeTransaction(Transaction transaction) throws InvalidTransactionStatusException {
            transactionStateMachine.transition(transaction, PaymentStatus.FINALIZED);
        }

        // Read-only with snapshot isolation
        @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
        public Transaction findTransactionById(Long transactionId) throws TransactionNotFoundException {
            return transactionRepository.findById(transactionId)
                    .orElseThrow(() -> new TransactionNotFoundException(transactionId));
        }

        // Standard isolation for status updates
        @Transactional(isolation = Isolation.READ_COMMITTED)
        public void updateTransactionPaymentStatus(long transactionId, PaymentStatus paymentStatus)
                throws InvalidTransactionStatusException, TransactionNotFoundException {
            transactionStateMachine.transition(transactionId, paymentStatus);
        }

        // Helper methods remain unchanged
        private void updateTransactionGatewayDetails(Transaction transaction, String maskedCard,
                                                     PaymentGateway gateway, IPaymentResponse response) {
            transaction.setPaymentGateway(gateway);
            transaction.setPaymentGatewayTransactionId(response.getGatewayTransactionID());
            transaction.setMaskedCard(maskedCard);
        }

        private void transitionToSubmittedStatus(Transaction transaction) {
            try {
                transactionStateMachine.transition(transaction, PaymentStatus.SUBMITTED);
            } catch (InvalidTransactionStatusException e) {
                throw new IllegalStateException("Failed to submit transaction", e);
            }
        }
    }