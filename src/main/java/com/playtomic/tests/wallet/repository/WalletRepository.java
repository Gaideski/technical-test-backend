package com.playtomic.tests.wallet.repository;

import com.playtomic.tests.wallet.model.dto.Transaction;
import com.playtomic.tests.wallet.model.dto.Wallet;
import jakarta.transaction.RollbackException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByAccountId(String accountId);

    @Query(value = "SELECT w.* FROM wallets w LEFT JOIN transactions t ON w.wallet_id = t.wallet_id WHERE w.account_id = :accountId",
            nativeQuery = true)
    Optional<Wallet> findByAccountIdWithTransactions(@Param("accountId") String accountId);

    @Modifying
    @Query(value = "UPDATE wallets SET funds = funds + :amount WHERE wallet_id = :walletId",
            nativeQuery = true)
    @Transactional(
            isolation = Isolation.SERIALIZABLE,
            propagation = Propagation.MANDATORY,
            rollbackFor = {RollbackException.class}
    )
    int addFunds(@Param("walletId") Long walletId, @Param("amount") Long amount) throws RollbackException;

    @Modifying
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "UPDATE wallets SET funds = funds - :amount WHERE wallet_id = :walletId AND funds >= :amount",
            nativeQuery = true)
    int withdrawFunds(@Param("walletId") Long walletId, @Param("amount") BigDecimal amount);

    @Query(value = "SELECT * FROM transactions WHERE ttl < :now",
            nativeQuery = true)
    List<Transaction> findExpiredTransactions(@Param("now") Date now);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM transactions WHERE ttl < :now",
            nativeQuery = true)
    int deleteExpiredTransactions(@Param("now") Date now);
}