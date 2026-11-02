package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.InspectionFeeSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InspectionFeeSettlementRepository extends JpaRepository<InspectionFeeSettlement, UUID> {
    Optional<InspectionFeeSettlement> findByInspectionOrderId(UUID inspectionOrderId);
    boolean existsByInspectionOrderId(UUID inspectionOrderId);
}

