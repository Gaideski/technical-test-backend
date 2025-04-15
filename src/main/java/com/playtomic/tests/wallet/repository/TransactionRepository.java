package com.playtomic.tests.wallet.repository;

import com.playtomic.tests.wallet.model.dto.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;


public interface TransactionRepository extends JpaRepository<Transaction, Long> {



}
