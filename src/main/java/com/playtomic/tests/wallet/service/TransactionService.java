package com.playtomic.tests.wallet.service;

import com.playtomic.tests.wallet.model.constants.PaymentGateway;
import com.playtomic.tests.wallet.model.constants.PaymentStatus;
import com.playtomic.tests.wallet.model.dto.Transaction;
import com.playtomic.tests.wallet.model.dto.Wallet;
import com.playtomic.tests.wallet.model.exceptions.InvalidTransactionStatusException;
import com.playtomic.tests.wallet.model.exceptions.TransactionNotFoundException;
import com.playtomic.tests.wallet.model.requests.PaymentRequest;
import com.playtomic.tests.wallet.model.responses.IPaymentResponse;
import com.playtomic.tests.wallet.repository.TransactionRepository;
import com.playtomic.tests.wallet.utils.IdempotencyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionStateMachineService transactionStateMachine;

    @Transactional
    public Transaction createInitialTransaction(Wallet wallet, PaymentRequest paymentRequest) throws InvalidTransactionStatusException {
        Transaction transaction = new Transaction();
        transaction.setWallet(wallet);
        transaction.setAmount(paymentRequest.getAmount());
        transaction.setPaymentMethod(paymentRequest.getPaymentMethod());
        transaction.setPaymentType(paymentRequest.getPaymentType());
        transaction.setIdempotencyKey(IdempotencyUtils.generateIdempotenceKey(paymentRequest));

        // Initial status set through state machine
        return transactionStateMachine.transition(transaction, PaymentStatus.CREATED);
    }

    @Transactional
    public void processPaymentGatewayResponse(long transactionId, String maskedCard,
                                              PaymentGateway gateway, IPaymentResponse response)
            throws TransactionNotFoundException {

        Transaction transaction = findTransactionById(transactionId);
        updateTransactionGatewayDetails(transaction, maskedCard, gateway, response);
        transitionToSubmittedStatus(transaction);
    }

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
            // Handle or rethrow as business exception
            throw new IllegalStateException("Failed to submit transaction", e);
        }
    }


    @Transactional
    public void finalizeTransaction(Transaction transaction) throws InvalidTransactionStatusException {
        transactionStateMachine.transition(transaction, PaymentStatus.FINALIZED);
    }

    public Transaction findTransactionById(Long transactionId) throws TransactionNotFoundException {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    public void updateTransactionPaymentStatus(long transactionId, PaymentStatus paymentStatus) throws InvalidTransactionStatusException, TransactionNotFoundException {
        var transaction = transactionStateMachine.transition(transactionId, paymentStatus);
    }
}
