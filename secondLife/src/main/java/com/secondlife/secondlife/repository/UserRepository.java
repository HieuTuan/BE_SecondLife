package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT DISTINCT u FROM User u " +
           "LEFT JOIN FETCH u.profile " +
           "LEFT JOIN FETCH u.userRoles ur " +
           "LEFT JOIN FETCH ur.role r " +
           "LEFT JOIN FETCH r.rolePermissions rp " +
           "LEFT JOIN FETCH rp.permission " +
           "WHERE LOWER(u.email) = LOWER(:email)")
    Optional<User> findByEmailWithAuthoritiesIgnoreCase(@Param("email") String email);

    @Query("SELECT DISTINCT u FROM User u " +
           "LEFT JOIN FETCH u.profile " +
           "LEFT JOIN FETCH u.userRoles ur " +
           "LEFT JOIN FETCH ur.role r " +
           "LEFT JOIN FETCH r.rolePermissions rp " +
           "LEFT JOIN FETCH rp.permission " +
           "WHERE u.id = :id")
    Optional<User> findByIdWithAuthorities(@Param("id") UUID id);

    @Query("SELECT COUNT(DISTINCT u) FROM User u " +
           "JOIN u.userRoles ur " +
           "JOIN ur.role r " +
           "WHERE r.code = 'ADMIN' AND u.accountStatus = 'ACTIVE'")
    long countActiveAdmins();
}

