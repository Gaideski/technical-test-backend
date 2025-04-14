package com.playtomic.tests.wallet.repository;

import com.playtomic.tests.wallet.model.dto.Transaction;
import com.playtomic.tests.wallet.model.dto.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByAccountId(String accountId);

    @Query("SELECT w FROM Wallet w LEFT JOIN FETCH w.transactions WHERE w.accountId = :accountId")
    Optional<Wallet> findByAccountIdWithTransactions(@Param("accountId") String accountId);

    @Modifying
    @Query("UPDATE Wallet w SET w.funds = w.funds + :amount WHERE w.walletId = :walletId")
    int addFunds(@Param("walletId") Long walletId, @Param("amount") BigDecimal amount);

    @Modifying
    @Transactional
    @Query("UPDATE Wallet w SET w.funds = w.funds - :amount WHERE w.walletId = :walletId AND w.funds >= :amount")
    int withdrawFunds(@Param("walletId") Long walletId, @Param("amount") BigDecimal amount);

    @Query("SELECT t FROM Transaction t WHERE t.ttl < :now")
    List<Transaction> findExpiredTransactions(@Param("now") Date now);

    @Modifying
    @Transactional
    @Query("DELETE FROM Transaction t WHERE t.ttl < :now")
    int deleteExpiredTransactions(@Param("now") Date now);
}