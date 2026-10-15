package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.InspectionSealLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InspectionSealLogRepository extends JpaRepository<InspectionSealLog, UUID> {
    List<InspectionSealLog> findByTamperSealIdOrderByScannedAtDesc(String tamperSealId);
    List<InspectionSealLog> findByInspectionOrderIdOrderByScannedAtAsc(UUID inspectionOrderId);
    boolean existsByTamperSealIdAndScanStage(String tamperSealId, String scanStage);
}
