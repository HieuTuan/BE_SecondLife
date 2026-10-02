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

    @Query("SELECT MAX(n.offeredPrice) FROM Negotiation n WHERE n.post.id = :postId AND n.buyer.id = :buyerId AND n.status = 'REJECTED'")
    Optional<BigDecimal> findMaxRejectedPrice(@Param("postId") UUID postId, @Param("buyerId") UUID buyerId);

    @Query("SELECT COUNT(n) FROM Negotiation n WHERE n.post.id = :postId AND n.buyer.id = :buyerId AND n.status = 'REJECTED'")
    long countRejectedNegotiations(@Param("postId") UUID postId, @Param("buyerId") UUID buyerId);

    boolean existsByPostIdAndBuyerIdAndStatusIn(UUID postId, UUID buyerId, List<NegotiationStatus> statuses);
}
