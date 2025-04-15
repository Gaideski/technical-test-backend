package com.playtomic.tests.wallet.repository;

import com.playtomic.tests.wallet.model.constants.PaymentStatus;
import com.playtomic.tests.wallet.model.dto.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Modifying
    @Query("UPDATE Transaction t SET t.paymentStatus = :status WHERE t.transactionId = :transactionId")
    @Transactional(propagation = Propagation.MANDATORY)
    int updatePaymentStatus(@Param("transactionId") Long transactionId,
                            @Param("status") PaymentStatus status);


}
