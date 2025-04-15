package com.playtomic.tests.wallet.model.exceptions;

import com.playtomic.tests.wallet.model.dto.Transaction;
import com.playtomic.tests.wallet.model.dto.TransactionDto;
import com.playtomic.tests.wallet.model.requests.IPaymentRequest;
import lombok.Getter;

@Getter
public class TransactionIdempotencyViolation extends Exception {
    TransactionDto existingTransaction;
    IPaymentRequest paymentAttempt;
    public TransactionIdempotencyViolation(String msg, Transaction existingTransaction, IPaymentRequest paymentAttempt) {
        super(msg);
        this.existingTransaction=new TransactionDto(existingTransaction);
        this.paymentAttempt=paymentAttempt;
    }

}
