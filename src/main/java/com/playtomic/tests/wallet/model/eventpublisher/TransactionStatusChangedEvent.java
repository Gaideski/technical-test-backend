package com.playtomic.tests.wallet.model.eventpublisher;

import com.playtomic.tests.wallet.model.constants.PaymentStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TransactionStatusChangedEvent extends ApplicationEvent {
    private final Long walletId;
    private final Long transactionId;
    private final PaymentStatus oldStatus;
    private final PaymentStatus newStatus;

    public TransactionStatusChangedEvent(Object source, Long walletId, Long transactionId,
                                         PaymentStatus oldStatus, PaymentStatus newStatus) {
        super(source);
        this.walletId = walletId;
        this.transactionId = transactionId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }
}
