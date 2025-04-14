package com.playtomic.tests.wallet.service;

import com.playtomic.tests.wallet.model.constants.PaymentGatewayProvider;
import com.playtomic.tests.wallet.model.constants.PaymentStatus;
import com.playtomic.tests.wallet.model.dto.Transaction;
import com.playtomic.tests.wallet.model.dto.Wallet;
import com.playtomic.tests.wallet.model.exceptions.TransactionNotFoundException;
import com.playtomic.tests.wallet.model.exceptions.WalletNotFoundException;
import com.playtomic.tests.wallet.model.requests.PaymentRequest;
import com.playtomic.tests.wallet.model.responses.IPaymentResponse;
import com.playtomic.tests.wallet.repository.TransactionRepository;
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


    public Transaction createInitialTransaction(Wallet wallet, PaymentRequest paymentRequest) throws WalletNotFoundException {

        Transaction transaction = new Transaction();
        transaction.setWallet(wallet);
        transaction.setAmount(paymentRequest.getAmount());
        transaction.setPaymentMethod(paymentRequest.getPaymentMethod());
        transaction.setPaymentStatus(PaymentStatus.CREATED);
        transaction.setIdempotencyKey(paymentRequest.getSessionId()); // Using sessionId as idempotency key

        // Payment provider will be set by the paymentProcessor after getting the suitable provider

        return transactionRepository.save(transaction);

    }

    @Transactional(isolation = Isolation.SERIALIZABLE,
            propagation = Propagation.REQUIRES_NEW)
    public void setProviderForTransaction(long transactionId, PaymentGatewayProvider provider, IPaymentResponse response) throws TransactionNotFoundException {
        var transaction = findTransactionById(transactionId);

        transaction.setProvider(provider);
        transaction.setPaymentStatus(PaymentStatus.SUBMITTED);
        transaction.setProviderTransactionId(response.getGatewayTransactionID());
        transactionRepository.save(transaction);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateTransactionPaymentStatus(long transactionId, PaymentStatus status) throws TransactionNotFoundException {
        transactionRepository.updatePaymentStatus(transactionId, status);

    }

    public Transaction findTransactionById(Long transactionId) throws TransactionNotFoundException {
        return transactionRepository.findById(transactionId).orElseThrow(
                () -> new TransactionNotFoundException(transactionId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeTransaction(Transaction transaction) {
        transaction.setFinishedAt(new Date());
        transaction.setPaymentStatus(PaymentStatus.FINALIZED);
        transactionRepository.save(transaction);
    }
}
