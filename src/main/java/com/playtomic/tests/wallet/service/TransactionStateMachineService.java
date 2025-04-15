package com.playtomic.tests.wallet.service;

import com.playtomic.tests.wallet.model.constants.PaymentStatus;
import com.playtomic.tests.wallet.model.dto.Transaction;
import com.playtomic.tests.wallet.model.eventpublisher.ITransactionEventPublisher;
import com.playtomic.tests.wallet.model.exceptions.InvalidTransactionStatusException;
import com.playtomic.tests.wallet.model.exceptions.TransactionNotFoundException;
import com.playtomic.tests.wallet.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class TransactionStateMachineService {

    private final TransactionRepository transactionRepository;
    private final ITransactionEventPublisher eventPublisher;


    @Transactional
    public Transaction transition(Long transactionId, PaymentStatus newStatus)
            throws TransactionNotFoundException, InvalidTransactionStatusException {

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        return transition(transaction, newStatus);
    }

    @Transactional
    public Transaction transition(Transaction transaction, PaymentStatus newStatus)
            throws InvalidTransactionStatusException {

        validateTransition(transaction.getPaymentStatus(), newStatus);
        applyStatusSpecificActions(transaction, newStatus);
        var oldStatus = transaction.getPaymentStatus();
        transaction.setPaymentStatus(newStatus);

        transaction = transactionRepository.save(transaction);
        eventPublisher.publishTransactionStatusChanged(
                transaction.getWallet().getWalletId(),
                transaction.getTransactionId(),
                oldStatus,
                newStatus
        );
        return transaction;
    }

    private void validateTransition(PaymentStatus currentStatus, PaymentStatus newStatus)
            throws InvalidTransactionStatusException {
        if (currentStatus == null && newStatus.equals(PaymentStatus.CREATED)) {
            return;
        }

        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new InvalidTransactionStatusException(
                    String.format("Invalid status transition from %s to %s", currentStatus, newStatus));
        }
    }

    private void applyStatusSpecificActions(Transaction transaction, PaymentStatus newStatus) {
        if (newStatus == PaymentStatus.FINALIZED) {
            transaction.setFinishedAt(new Date());
        }
    }
}