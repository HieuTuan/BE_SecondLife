package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.InspectionFeeSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionFeeSettlementRepository extends JpaRepository<InspectionFeeSettlement, UUID> {
    Optional<InspectionFeeSettlement> findByInspectionOrderId(UUID inspectionOrderId);
    boolean existsByInspectionOrderId(UUID inspectionOrderId);

    @Query("""
            select s.inspectionOrder.id as inspectionOrderId, s.centerPayoutAmount as amount
            from InspectionFeeSettlement s
            where s.centerRecipientUser.id = :centerId and s.inspectionOrder.id in :inspectionOrderIds
            """)
    List<CenterPayout> findCenterPayouts(@Param("centerId") UUID centerId,
                                       @Param("inspectionOrderIds") Collection<UUID> inspectionOrderIds);

    interface CenterPayout {
        UUID getInspectionOrderId();
        BigDecimal getAmount();
    }
}

