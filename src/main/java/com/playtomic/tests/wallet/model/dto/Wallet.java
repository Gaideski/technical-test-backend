package com.playtomic.tests.wallet.model.dto;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.playtomic.tests.wallet.model.constants.AccountStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "wallets")
@NoArgsConstructor
@RequiredArgsConstructor
@Getter
@Setter
@ToString(exclude = "transactions")  // Exclude from toString to prevent recursion
@EqualsAndHashCode(exclude = "transactions")  // Exclude from equals/hashCode
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long walletId;

    @NonNull
    @Column(nullable = false)
    private String accountId;

    @NonNull
    @Column(nullable = false)
    private BigDecimal funds = BigDecimal.ZERO;

    @Column(nullable = false)
    private Date createdAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountStatus accountStatus;

    @JsonManagedReference
    @OneToMany(mappedBy = "wallet", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Transaction> transactions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = new Date();
            accountStatus = AccountStatus.ACTIVE;
        }
    }
}