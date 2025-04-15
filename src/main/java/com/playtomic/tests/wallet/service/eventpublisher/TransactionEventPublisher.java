package com.playtomic.tests.wallet.service.eventpublisher;

import com.playtomic.tests.wallet.model.constants.PaymentStatus;
import com.playtomic.tests.wallet.model.eventpublisher.ITransactionEventPublisher;
import com.playtomic.tests.wallet.model.eventpublisher.TransactionStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionEventPublisher implements ITransactionEventPublisher {
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publishTransactionStatusChanged(Long walletId, Long transactionId, PaymentStatus oldStatus, PaymentStatus newStatus) {
        eventPublisher.publishEvent(
                new TransactionStatusChangedEvent(this, walletId, transactionId, oldStatus, newStatus)
        );
    }
}
