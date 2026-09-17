package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT DISTINCT r FROM Role r " +
           "LEFT JOIN FETCH r.rolePermissions rp " +
           "LEFT JOIN FETCH rp.permission " +
           "WHERE r.code = :code")
    Optional<Role> findByCodeWithPermissions(@Param("code") String code);
}
