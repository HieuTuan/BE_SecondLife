package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet_withdrawal_status_history")
@Getter @Setter @NoArgsConstructor
public class WalletWithdrawalStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "withdrawal_id", nullable = false)
    private WalletWithdrawal withdrawal;
    @Column(name = "old_status", length = 40) private String oldStatus;
    @Column(name = "new_status", nullable = false, length = 40) private String newStatus;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_id") private User actor;
    @Column(name = "action", nullable = false, length = 60) private String action;
    @Column(name = "reason", columnDefinition = "TEXT") private String reason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    public WalletWithdrawalStatusHistory(WalletWithdrawal withdrawal, String oldStatus, String newStatus,
                                         User actor, String action, String reason) {
        this.withdrawal = withdrawal; this.oldStatus = oldStatus; this.newStatus = newStatus;
        this.actor = actor; this.action = action; this.reason = reason; this.createdAt = Instant.now();
    }
}
