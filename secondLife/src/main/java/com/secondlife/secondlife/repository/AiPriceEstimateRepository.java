package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.AiPriceEstimate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AiPriceEstimateRepository extends JpaRepository<AiPriceEstimate, UUID> {
    Optional<AiPriceEstimate> findByListingIdAndRequestId(UUID listingId, UUID requestId);
    Optional<AiPriceEstimate> findFirstByListingIdOrderByCreatedAtDescIdDesc(UUID listingId);
    Page<AiPriceEstimate> findByListingIdOrderByCreatedAtDescIdDesc(UUID listingId, Pageable pageable);
}
