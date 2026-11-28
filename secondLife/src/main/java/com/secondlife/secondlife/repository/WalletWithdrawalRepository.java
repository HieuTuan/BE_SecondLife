package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.WalletWithdrawal;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletWithdrawalRepository extends JpaRepository<WalletWithdrawal, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletWithdrawal w WHERE w.id = :id")
    Optional<WalletWithdrawal> findByIdForUpdate(@Param("id") UUID id);

    Page<WalletWithdrawal> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<WalletWithdrawal> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Optional<WalletWithdrawal> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Page<WalletWithdrawal> findByDisputeDeadlineBeforeAndStatus( java.time.Instant deadline, String status, Pageable pageable);

    @Query(value = """
        SELECT id FROM wallet_withdrawals WHERE status = 'TRANSFERRED_AWAITING_REVIEW'
        AND dispute_deadline > now() AND dispute_deadline <= now() + interval '24 hours'
        AND coalesce(reminder_hours, 48) > CASE
          WHEN dispute_deadline <= now() + interval '1 hour' THEN 1
          WHEN dispute_deadline <= now() + interval '6 hours' THEN 6
          WHEN dispute_deadline <= now() + interval '12 hours' THEN 12 ELSE 24 END
        ORDER BY dispute_deadline LIMIT 200 FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    java.util.List<UUID> lockDueReminderIds();

    Page<WalletWithdrawal> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
