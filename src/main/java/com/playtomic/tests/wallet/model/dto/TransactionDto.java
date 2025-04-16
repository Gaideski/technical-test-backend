package com.playtomic.tests.wallet.model.dto;

import com.playtomic.tests.wallet.model.constants.PaymentGateway;
import com.playtomic.tests.wallet.model.constants.PaymentMethod;
import com.playtomic.tests.wallet.model.constants.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDto {
    private Long transactionId;
    private BigDecimal amount;
    private PaymentStatus status;
    private PaymentMethod paymentMethod;
    private PaymentGateway paymentGateway;
    private Date createdAt;
    private Date finishedAt;

    public TransactionDto(Transaction transaction) {
        this.transactionId = transaction.getTransactionId();
        this.amount = transaction.getAmount();
        this.status = transaction.getPaymentStatus();
        this.paymentMethod = transaction.getPaymentMethod();
        this.paymentGateway = transaction.getPaymentGateway();
        this.createdAt = transaction.getCreatedAt();
        this.finishedAt = transaction.getFinishedAt();
    }
}