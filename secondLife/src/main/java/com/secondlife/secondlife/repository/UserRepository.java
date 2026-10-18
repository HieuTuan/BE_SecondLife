package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.User;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForRoleUpdate(@Param("id") UUID id);

    @Query("SELECT COUNT(DISTINCT u) FROM User u " +
           "JOIN u.userRoles ur " +
           "JOIN ur.role r " +
           "WHERE r.code = 'ADMIN' AND u.accountStatus = 'ACTIVE'")
    long countActiveAdmins();

    @EntityGraph(attributePaths = {"profile", "userRoles.role"})
    @Query("select u from User u where u.id in :ids")
    List<User> findAllWithRolesByIdIn(@Param("ids") Collection<UUID> ids);

    List<User> findByManagerId(UUID managerId);
    @Query("""
        SELECT DISTINCT u.id FROM User u JOIN u.userRoles ur JOIN ur.role r
        JOIN r.rolePermissions rp JOIN rp.permission p
        WHERE r.code='STAFF' AND p.code='STAFF_DISPUTE_REVIEW' AND u.accountStatus='ACTIVE'
        """)
    List<UUID> findActiveOrderReviewStaffIds();

    @Query(value = """
            SELECT u.id FROM users u
            JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id
            LEFT JOIN role_permissions rp ON rp.role_id=r.id LEFT JOIN permissions p ON p.id=rp.permission_id
            WHERE u.account_status='ACTIVE' AND u.id<>:reporterId AND u.id<>:sellerId
            GROUP BY u.id
            HAVING (bool_or(r.code='STAFF') AND bool_or(p.code='STAFF_FRAUD_REVIEW'))
                OR (bool_or(r.code='ADMIN') AND bool_or(p.code='ADMIN_RBAC_MANAGE'))
            """, nativeQuery = true)
    List<UUID> findEligibleReviewers(@Param("reporterId") UUID reporterId, @Param("sellerId") UUID sellerId);
}

