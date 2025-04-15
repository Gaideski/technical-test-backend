package com.playtomic.tests.wallet.repository;

import com.playtomic.tests.wallet.model.dto.Transaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;


public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Transaction findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t JOIN FETCH t.wallet WHERE t.idempotencyKey = :idempotencyKey")
    Optional<Transaction> findByIdempotencyKeyWithLock(@Param("idempotencyKey") String idempotencyKey);
}
