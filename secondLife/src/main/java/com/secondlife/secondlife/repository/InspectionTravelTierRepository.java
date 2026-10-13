package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.InspectionTravelTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionTravelTierRepository extends JpaRepository<InspectionTravelTier, UUID> {

    List<InspectionTravelTier> findByStatusOrderByTierNumberAsc(String status);

    default List<InspectionTravelTier> findAllActive() {
        return findByStatusOrderByTierNumberAsc("ACTIVE");
    }

    Optional<InspectionTravelTier> findByTierNumber(Integer tierNumber);
}
