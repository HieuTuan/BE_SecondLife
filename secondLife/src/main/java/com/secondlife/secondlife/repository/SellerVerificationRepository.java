package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.enums.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SellerVerificationRepository extends JpaRepository<SellerVerification, UUID> {

    Optional<SellerVerification> findTopByUserIdOrderBySubmittedAtDesc(UUID userId);

    boolean existsByUserIdAndStatus(UUID userId, VerificationStatus status);

    @Query(value = "SELECT sv FROM SellerVerification sv " +
           "JOIN FETCH sv.user u " +
           "JOIN FETCH u.profile",
           countQuery = "SELECT COUNT(sv) FROM SellerVerification sv")
    Page<SellerVerification> findAllWithDetails(Pageable pageable);

    @Query(value = "SELECT sv FROM SellerVerification sv " +
           "JOIN FETCH sv.user u " +
           "JOIN FETCH u.profile " +
           "WHERE sv.status = :status",
           countQuery = "SELECT COUNT(sv) FROM SellerVerification sv WHERE sv.status = :status")
    Page<SellerVerification> findByStatusWithDetails(@Param("status") VerificationStatus status, Pageable pageable);

    @Query("SELECT sv FROM SellerVerification sv " +
           "JOIN FETCH sv.user u " +
           "JOIN FETCH u.profile " +
           "LEFT JOIN FETCH sv.reviewedBy " +
           "WHERE sv.id = :id")
    Optional<SellerVerification> findByIdWithDetails(@Param("id") UUID id);
}
