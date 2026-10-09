package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "commission_rule_audits") @Immutable
@Getter @Setter @NoArgsConstructor
public class CommissionRuleAudit {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private UUID ruleId;
    @Column(nullable = false) private UUID actorId;
    @Column(nullable = false, length = 20) private String action;
    @Column(columnDefinition = "TEXT") private String oldValue;
    @Column(nullable = false, columnDefinition = "TEXT") private String newValue;
    @Column(nullable = false, length = 1000) private String reason;
    @Column(nullable = false) private Instant changedAt;
}
