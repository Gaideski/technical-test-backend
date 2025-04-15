package com.playtomic.tests.wallet.model.exceptions;

public class InvalidTransactionStatusException extends Exception {
    public InvalidTransactionStatusException(String msg) {
        super(msg);
    }
}
