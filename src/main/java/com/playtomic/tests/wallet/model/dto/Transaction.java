package com.playtomic.tests.wallet.model.dto;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.playtomic.tests.wallet.model.constants.PaymentGateway;
import com.playtomic.tests.wallet.model.constants.PaymentMethod;
import com.playtomic.tests.wallet.model.constants.PaymentStatus;
import com.playtomic.tests.wallet.model.constants.PaymentType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Data
@Entity
@Table(name = "transactions")
@NoArgsConstructor
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;

    @Column(unique = true, nullable = false)
    private String idempotencyKey;

    @Column(unique = true)
    private String paymentGatewayTransactionId;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @NonNull
    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private int paymentFailedCounter;

    @Column
    private String maskedCard;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    private PaymentGateway paymentGateway;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType paymentType;


    @Column(nullable = false)
    private Date createdAt;

    @Column
    private Date finishedAt;


    // It holds the last status for a given order.
    // In a production env, it should capture the changes for this field using CDC to populate an transactionHistory service
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    //TTL so this entry may be deleted and not overcrowd our wallet service db without need
    @Column(nullable = false)
    private Date ttl;

    @Version
    private Long version;

    // Idea for refund. When refund is issued, we can fill both fields to correlate them
    // This will help to track the refund, as well avoid any foreign key issues when deleting older transactions
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_transaction_id")
    private Transaction originalTransaction;

    @OneToMany(mappedBy = "originalTransaction", cascade = CascadeType.ALL)
    private List<Transaction> refundTransactions = new ArrayList<>();


    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = new Date();
        }
        if (ttl == null) {
            // Set TTL to 30 days from creation by default
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(createdAt);
            calendar.add(Calendar.DAY_OF_MONTH, 30);
            ttl = calendar.getTime();
        }
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId=" + transactionId +
                ", amount=" + amount +
                ", status=" + paymentStatus +
                ", paymentMethod=" + paymentMethod +
                ", paymentGateway=" + paymentGateway +
                ", createdAt=" + createdAt +
                ", finishedAt=" + finishedAt +
                '}';
    }
}
