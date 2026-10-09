package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.Negotiation;
import com.secondlife.secondlife.enums.NegotiationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface NegotiationRepository extends JpaRepository<Negotiation, UUID> {

    @Query("SELECT MAX(n.offeredPrice) FROM Negotiation n WHERE n.post.id = :postId AND n.buyer.id = :buyerId AND n.status = com.secondlife.secondlife.enums.NegotiationStatus.REJECTED")
    Optional<BigDecimal> findMaxRejectedPrice(@Param("postId") UUID postId, @Param("buyerId") UUID buyerId);

    @Query("SELECT COUNT(n) FROM Negotiation n WHERE n.post.id = :postId AND n.buyer.id = :buyerId AND n.status = com.secondlife.secondlife.enums.NegotiationStatus.REJECTED")
    long countRejectedNegotiations(@Param("postId") UUID postId, @Param("buyerId") UUID buyerId);

    @Query("SELECT COUNT(n) > 0 FROM Negotiation n WHERE n.post.id = :postId AND n.buyer.id = :buyerId AND n.status IN :statuses")
    boolean existsByPostIdAndBuyerIdAndStatusIn(@Param("postId") UUID postId, @Param("buyerId") UUID buyerId, @Param("statuses") List<NegotiationStatus> statuses);

    org.springframework.data.domain.Page<Negotiation> findByBuyerId(UUID buyerId, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Negotiation> findByPostUserId(UUID sellerId, org.springframework.data.domain.Pageable pageable);

    List<Negotiation> findByPostIdAndStatus(UUID postId, NegotiationStatus status);

    List<Negotiation> findByStatusAndExpiredAtBefore(NegotiationStatus status, java.time.Instant time);
}
