package com.playtomic.tests.wallet.service;

import com.playtomic.tests.wallet.model.constants.PaymentGateway;
import com.playtomic.tests.wallet.model.constants.PaymentStatus;
import com.playtomic.tests.wallet.model.dto.Transaction;
import com.playtomic.tests.wallet.model.dto.Wallet;
import com.playtomic.tests.wallet.model.exceptions.TransactionNotFoundException;
import com.playtomic.tests.wallet.model.requests.PaymentRequest;
import com.playtomic.tests.wallet.model.responses.IPaymentResponse;
import com.playtomic.tests.wallet.repository.TransactionRepository;
import com.playtomic.tests.wallet.utils.IdempotencyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;


    @Transactional(isolation = Isolation.SERIALIZABLE,
            propagation = Propagation.REQUIRES_NEW)
    public Transaction createInitialTransaction(Wallet wallet, PaymentRequest paymentRequest) {

        // This method can be reworked to create the transaction for different purposes
        // like expending the money/transfer between wallets
        Transaction transaction = new Transaction();
        transaction.setWallet(wallet);
        transaction.setAmount(paymentRequest.getAmount());
        transaction.setPaymentMethod(paymentRequest.getPaymentMethod());
        transaction.setPaymentStatus(PaymentStatus.CREATED);
        transaction.setPaymentType(paymentRequest.getPaymentType());
        transaction.setIdempotencyKey(IdempotencyUtils.generateIdempotenceKey(paymentRequest));

        // Payment provider will be set by the paymentProcessor after getting the suitable provider

        return transactionRepository.save(transaction);

    }

    @Transactional(isolation = Isolation.REPEATABLE_READ,
            propagation = Propagation.REQUIRES_NEW)
    public void setProviderForTransaction(long transactionId, String credit_card, PaymentGateway gateway, IPaymentResponse response) throws TransactionNotFoundException {
        var transaction = findTransactionById(transactionId);
        transaction.setPaymentGateway(gateway);
        transaction.setPaymentStatus(PaymentStatus.SUBMITTED);
        transaction.setPaymentGatewayTransactionId(response.getGatewayTransactionID());
        transaction.setMaskedCard(credit_card);
        transactionRepository.save(transaction);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ,
            propagation = Propagation.REQUIRES_NEW)
    public void updateTransactionPaymentStatus(long transactionId, PaymentStatus status) throws TransactionNotFoundException {
        //
        var transaction = findTransactionById(transactionId);
        transaction.setPaymentStatus(status);
        transactionRepository.save(transaction);
    }

    public Transaction findTransactionById(Long transactionId) throws TransactionNotFoundException {
        return transactionRepository.findById(transactionId).orElseThrow(
                () -> new TransactionNotFoundException(transactionId));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE,
            propagation = Propagation.REQUIRES_NEW)
    public void finalizeTransaction(Transaction transaction) {
        transaction.setFinishedAt(new Date());
        transaction.setPaymentStatus(PaymentStatus.FINALIZED);
        transactionRepository.save(transaction);
    }


}
