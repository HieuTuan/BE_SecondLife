package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_role_audit")
@Getter
@Setter
public class UserRoleAudit {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "role_code", nullable = false, length = 50)
    private String roleCode;
    @Column(nullable = false, length = 10)
    private String action;
    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
}
