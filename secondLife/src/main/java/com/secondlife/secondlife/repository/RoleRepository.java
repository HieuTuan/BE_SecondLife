package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    @Query("SELECT DISTINCT r FROM Role r LEFT JOIN FETCH r.rolePermissions rp LEFT JOIN FETCH rp.permission")
    List<Role> findAllWithPermissions();

    Optional<Role> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Role r WHERE r.code = :code")
    Optional<Role> findByCodeForUpdate(@Param("code") String code);

    boolean existsByCode(String code);

    @Query("SELECT DISTINCT r FROM Role r " +
           "LEFT JOIN FETCH r.rolePermissions rp " +
           "LEFT JOIN FETCH rp.permission " +
           "WHERE r.code = :code")
    Optional<Role> findByCodeWithPermissions(@Param("code") String code);
}
