package com.playtomic.tests.wallet.repository;

import com.playtomic.tests.wallet.model.dto.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByAccountId(String accountId);

    @Query("SELECT w FROM Wallet w LEFT JOIN FETCH w.transactions WHERE w.accountId = :accountId")
    Optional<Wallet> findByAccountIdWithTransactions(@Param("accountId") String accountId);

    @Query("SELECT w FROM Wallet w WHERE w.walletId = :walletId")
    Optional<Wallet> findById(@Param("walletId") Long walletId);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdWithLock(@Param("id") Long id);

    @Modifying
    @Query(value = "UPDATE wallets SET amount = amount + :amount, version = version + 1 " +
            "WHERE wallet_id = :walletId AND version = :version", nativeQuery = true)
    int addFundsWithVersionCheck(
            @Param("walletId") Long walletId,
            @Param("amount") BigDecimal amount,
            @Param("version") Long version
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") Long id);
}