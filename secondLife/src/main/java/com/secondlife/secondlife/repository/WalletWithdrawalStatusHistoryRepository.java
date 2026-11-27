package com.secondlife.secondlife.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
import com.secondlife.secondlife.entity.WalletWithdrawalStatusHistory;

public interface WalletWithdrawalStatusHistoryRepository extends JpaRepository<WalletWithdrawalStatusHistory, UUID> {
    List<WalletWithdrawalStatusHistory> findByWithdrawalIdOrderByCreatedAtAsc(UUID withdrawalId);
    List<WalletWithdrawalStatusHistory> findByWithdrawalIdAndWithdrawalUserIdOrderByCreatedAtAsc(UUID withdrawalId, UUID userId);
}
