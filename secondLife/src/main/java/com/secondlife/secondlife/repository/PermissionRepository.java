package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByCode(String code);
    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Permission p WHERE p.code = :code")
    Optional<Permission> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Permission p WHERE p.code IN :codes ORDER BY p.code")
    List<Permission> findByCodesForUpdate(@Param("codes") Collection<String> codes);

    @Query("SELECT DISTINCT rp.role.code FROM RolePermission rp WHERE rp.permission.id = :permissionId")
    Set<String> findAssignedRoleCodes(@Param("permissionId") UUID permissionId);
}
