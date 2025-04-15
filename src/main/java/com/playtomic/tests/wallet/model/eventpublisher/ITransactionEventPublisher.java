package com.playtomic.tests.wallet.model.eventpublisher;

import com.playtomic.tests.wallet.model.constants.PaymentStatus;

public interface ITransactionEventPublisher {
    void publishTransactionStatusChanged(Long walletId,
                                         Long transactionId,
                                         PaymentStatus oldStatus,
                                         PaymentStatus newStatus);
}

