package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.SePayTransferReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

public interface SePayTransferReceiptRepository extends JpaRepository<SePayTransferReceipt, Long> {
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = """
            WITH claimed AS (
                INSERT INTO sepay_transfer_receipts(transaction_id, code, amount, received_at)
                VALUES (:transactionId, :code, :amount, now())
                ON CONFLICT(transaction_id) DO NOTHING
                RETURNING 1
            )
            SELECT EXISTS(SELECT 1 FROM claimed)
            """, nativeQuery = true)
    boolean claimTransfer(@Param("transactionId") Long transactionId, @Param("code") String code,
                          @Param("amount") BigDecimal amount);
}
