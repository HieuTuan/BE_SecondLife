package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.UserRoleAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UserRoleAuditRepository extends JpaRepository<UserRoleAudit, UUID> {
    Page<UserRoleAudit> findByUserIdOrderByChangedAtDesc(UUID userId, Pageable pageable);
}
