package com.playtomic.tests.wallet.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
public class WalletDto {
    private Long walletId;
    private String accountId;
    private BigDecimal funds;
    private List<TransactionDto> recentTransactions;

    public WalletDto(Wallet wallet){
        this.walletId=wallet.getWalletId();
        this.funds=wallet.getFunds();
        this.accountId=wallet.getAccountId();
        if(!wallet.getTransactions().isEmpty()){
            setTransactions(wallet.getTransactions());
        }
    }

    public void setTransactions(List<Transaction> transactions) {
        this.recentTransactions = transactions.stream()
                .sorted((t1, t2) -> t2.getCreatedAt().compareTo(t1.getCreatedAt()))
                .limit(10)
                .map(t -> new TransactionDto(
                        t.getTransactionId(),
                        t.getAmount(),
                        t.getPaymentStatus(),
                        t.getPaymentMethod(),
                        t.getPaymentGateway(),
                        t.getCreatedAt(),
                        t.getFinishedAt()))
                .toList();
    }
}