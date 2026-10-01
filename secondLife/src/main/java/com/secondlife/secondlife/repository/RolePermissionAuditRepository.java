package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.RolePermissionAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RolePermissionAuditRepository extends JpaRepository<RolePermissionAudit, UUID> {
    Page<RolePermissionAudit> findByRoleIdOrderByChangedAtDesc(UUID roleId, Pageable pageable);
}
