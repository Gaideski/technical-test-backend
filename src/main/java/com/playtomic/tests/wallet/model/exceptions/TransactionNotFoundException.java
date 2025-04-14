package com.playtomic.tests.wallet.model.exceptions;

public class TransactionNotFoundException extends Exception {
    public TransactionNotFoundException(long transactionId) {
        super("No transaction found for: " + transactionId);
    }
}
