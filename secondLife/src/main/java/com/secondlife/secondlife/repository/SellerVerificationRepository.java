package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.enums.EkycStatus;
import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.enums.RiskStatus;
import com.secondlife.secondlife.enums.SellerVerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SellerVerificationRepository extends JpaRepository<SellerVerification, UUID> {

    Optional<SellerVerification> findTopByUserIdOrderBySubmittedAtDesc(UUID userId);

    boolean existsByUserIdAndStatus(UUID userId, SellerVerificationStatus status);

    boolean existsByUserIdAndStatusIn(UUID userId, Collection<SellerVerificationStatus> statuses);

    boolean existsByDocumentNumberHashAndStatus(String documentNumberHash, SellerVerificationStatus status);

    long countByDocumentNumberHashAndStatus(String documentNumberHash, SellerVerificationStatus status);

    List<SellerVerification> findByDocumentNumberHash(String documentNumberHash);

    long countByUserIdAndEkycStatus(UUID userId, EkycStatus ekycStatus);

    @Query(value = "SELECT sv FROM SellerVerification sv " +
           "JOIN FETCH sv.user u " +
           "JOIN FETCH u.profile " +
           "LEFT JOIN FETCH sv.reviewedBy " +
           "WHERE (:status IS NULL OR sv.status = :status) " +
           "AND (:ekycStatus IS NULL OR sv.ekycStatus = :ekycStatus) " +
           "AND (:riskStatus IS NULL OR sv.riskStatus = :riskStatus) " +
           "AND (:reasonCode IS NULL OR sv.reasonCode = :reasonCode)",
           countQuery = "SELECT COUNT(sv) FROM SellerVerification sv " +
           "WHERE (:status IS NULL OR sv.status = :status) " +
           "AND (:ekycStatus IS NULL OR sv.ekycStatus = :ekycStatus) " +
           "AND (:riskStatus IS NULL OR sv.riskStatus = :riskStatus) " +
           "AND (:reasonCode IS NULL OR sv.reasonCode = :reasonCode)")
    Page<SellerVerification> findWithFilters(
            @Param("status") SellerVerificationStatus status,
            @Param("ekycStatus") EkycStatus ekycStatus,
            @Param("riskStatus") RiskStatus riskStatus,
            @Param("reasonCode") ReasonCode reasonCode,
            Pageable pageable);

    @Query("SELECT sv FROM SellerVerification sv " +
           "JOIN FETCH sv.user u " +
           "JOIN FETCH u.profile " +
           "LEFT JOIN FETCH sv.reviewedBy " +
           "WHERE sv.id = :id")
    Optional<SellerVerification> findByIdWithDetails(@Param("id") UUID id);
}
