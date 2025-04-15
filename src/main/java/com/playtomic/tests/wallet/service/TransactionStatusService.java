package com.playtomic.tests.wallet.service;

public class TransactionStatusService {
    // TODO: create cron task that polls the database for status and take action
    // Created -> submit payment
    // Submitted -> Expire (we don't have a way to validate the status if crashed)
    // Successful or Processing -> Update wallet (using both as same for POC purposes)


}
