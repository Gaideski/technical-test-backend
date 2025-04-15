package com.playtomic.tests.wallet.model.responses;

import com.playtomic.tests.wallet.model.dto.Transaction;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public record WalletDto(String userID, BigDecimal amount, List<Transaction> lastTransactions) {
    @Override
    public List<Transaction> lastTransactions() {
        return lastTransactions.stream()
                .sorted(Comparator.comparing(Transaction::getCreatedAt).reversed())
                .limit(10).toList();
    }
}
